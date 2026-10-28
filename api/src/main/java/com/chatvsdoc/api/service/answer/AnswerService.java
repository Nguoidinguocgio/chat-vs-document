package com.chatvsdoc.api.service.answer;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.port.LlmPort;
import com.chatvsdoc.api.port.LlmPort.LlmException;
import com.chatvsdoc.api.port.LlmPort.LlmRateLimitedException;
import com.chatvsdoc.api.port.LlmPort.LlmTimeoutException;
import com.chatvsdoc.api.port.LlmPort.LlmUsage;
import com.chatvsdoc.api.port.fake.FakeLlm;
import com.chatvsdoc.api.service.retrieval.ContextBlock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Sinh câu trả lời có trích dẫn (T13). LLM chạy trên luồng ảo riêng; luồng gọi đọc mảnh chữ với hạn
 * chờ: không có token đầu trong {@code LLM_FIRST_TOKEN_TIMEOUT_S} hoặc im lặng giữa chừng quá hạn →
 * {@code Failed(timeout)}. Mọi luồng kết thúc bằng đúng một {@code Done}/{@code Failed} — trừ khi
 * luồng gọi bị interrupt (người dùng dừng / ngắt kết nối): khi đó đóng LLM và không phát gì thêm.
 * Không ghi câu hỏi, nguồn hay câu trả lời vào log.
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    /** Mảnh chữ / kết thúc / lỗi do luồng LLM đẩy vào hàng đợi. */
    private sealed interface Signal {}

    private record Chunk(String text) implements Signal {}

    private record End(LlmUsage usage) implements Signal {}

    private record Thrown(RuntimeException e) implements Signal {}

    private final LlmPort llm;
    private final Duration timeout;

    public AnswerService(@Lazy LlmPort llm, AppProperties props) {
        this.llm = llm;
        this.timeout = Duration.ofSeconds(props.llm().firstTokenTimeoutS());
    }

    /**
     * Ném {@link FakeLlm.SimulatedConnectionDrop} lên trên (chỉ LLM giả) để lớp SSE đóng kết nối
     * đột ngột.
     */
    public void stream(
            String question, List<ContextBlock> blocks, String lang, Consumer<AnswerEvent> sink)
            throws InterruptedException {
        long start = System.nanoTime();
        BlockingQueue<Signal> queue = new LinkedBlockingQueue<>();
        Thread producer =
                Thread.ofVirtual()
                        .name("llm-stream")
                        .start(
                                () -> {
                                    try {
                                        LlmUsage usage =
                                                llm.stream(
                                                        Prompts.messages(question, blocks, lang),
                                                        d -> queue.add(new Chunk(d)));
                                        queue.add(new End(usage));
                                    } catch (RuntimeException e) {
                                        queue.add(new Thrown(e));
                                    }
                                });
        Integer ttftMs = null;
        StreamParser parser = new StreamParser(blocks, sink);
        try {
            while (true) {
                Signal s = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (s == null) {
                    producer.interrupt();
                    finishFailed(sink, "timeout", start, ttftMs != null);
                    return;
                }
                if (s instanceof Chunk c) {
                    if (ttftMs == null && !c.text().isEmpty()) {
                        ttftMs = (int) ((System.nanoTime() - start) / 1_000_000);
                    }
                    parser.feed(c.text());
                } else if (s instanceof End end) {
                    StreamParser.Outcome o = parser.finish();
                    int totalMs = (int) ((System.nanoTime() - start) / 1_000_000);
                    log.info(
                            "answer_completed",
                            kv("answer_type", o.answerType()),
                            kv("uncited", o.uncited()),
                            kv("citations", parser.citationCount()),
                            kv("ttft_ms", ttftMs),
                            kv("total_ms", totalMs),
                            kv("prompt_version", Prompts.VERSION));
                    sink.accept(
                            new AnswerEvent.Done(
                                    o.answerType(),
                                    o.missing(),
                                    o.uncited(),
                                    ttftMs,
                                    totalMs,
                                    end.usage() == null ? null : end.usage().inputTokens(),
                                    end.usage() == null ? null : end.usage().outputTokens()));
                    return;
                } else if (s instanceof Thrown err) {
                    RuntimeException e = err.e();
                    if (e instanceof FakeLlm.SimulatedConnectionDrop drop) {
                        throw drop;
                    }
                    finishFailed(sink, code(e), start, ttftMs != null);
                    return;
                }
            }
        } finally {
            // Bị interrupt, sink ném lỗi (client đã ngắt) hoặc đã kết thúc: luôn dừng luồng LLM.
            producer.interrupt();
        }
    }

    static String code(RuntimeException e) {
        if (e instanceof LlmTimeoutException) {
            return "timeout";
        }
        if (e instanceof LlmRateLimitedException) {
            return "rate_limited";
        }
        if (e instanceof CancellationException) {
            return "llm_error";
        }
        if (!(e instanceof LlmException)) {
            log.error("llm_unexpected_error", e);
        }
        return "llm_error";
    }

    private static void finishFailed(
            Consumer<AnswerEvent> sink, String code, long start, boolean hadTokens) {
        log.warn(
                "answer_failed",
                kv("error_code", code),
                kv("had_tokens", hadTokens),
                kv("total_ms", (System.nanoTime() - start) / 1_000_000));
        sink.accept(new AnswerEvent.Failed(code));
    }
}
