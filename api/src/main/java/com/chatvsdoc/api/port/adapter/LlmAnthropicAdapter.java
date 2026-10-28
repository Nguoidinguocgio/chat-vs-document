package com.chatvsdoc.api.port.adapter;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.chatvsdoc.api.port.LlmPort;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM thật: Anthropic Messages API, chế độ streaming, qua Anthropic Java SDK chính thức (T13 mục
 * 3.6).
 *
 * <ul>
 *   <li>Model {@code LLM_MODEL} (mặc định {@code claude-sonnet-5-5}, đã chốt cho dự án).
 *   <li>{@code thinking: {type: "between_tools"}} — mức suy nghĩ thấp nhất của Claude Sonnet 5.5
 *       ({@code disabled} trả 400), giữ thời gian tới token đầu thấp; chỉ hợp lệ ở effort ≤ {@code
 *       high} nên đặt {@code medium}.
 *   <li>Không gửi {@code temperature}/{@code top_p}/{@code top_k} (400 từ API 2026-10-21); tính bám
 *       nguồn đến từ prompt.
 *   <li>Fallback phía máy chủ {@code fallbacks: "default"} (beta {@code
 *       server-side-fallback-2026-07-01}): từ chối loại {@code cyber}/{@code frontier_llm} được
 *       chạy lại trên Claude Sonnet 5 (với {@code between_tools} sẽ chạy ở chế độ tắt suy nghĩ).
 *   <li>{@code stop_reason == refusal} → {@link LlmException}. SDK chỉ thử lại 1 lần (trước token
 *       đầu); đã có token thì không thử lại.
 * </ul>
 *
 * Khóa API đọc từ biến môi trường {@code LLM_API_KEY}; không ghi prompt/câu trả lời vào log.
 */
public class LlmAnthropicAdapter implements LlmPort {

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    public LlmAnthropicAdapter(
            String apiKey, String model, long maxTokens, Duration requestTimeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_PROVIDER=anthropic cần LLM_API_KEY");
        }
        this.client =
                AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        .maxRetries(1)
                        .timeout(requestTimeout)
                        .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /** Dựng yêu cầu (công khai để test kiểm tra tham số mà không gọi mạng). */
    public MessageCreateParams buildParams(List<LlmMessage> messages) {
        String system =
                messages.stream()
                        .filter(m -> m.role() == LlmMessage.Role.system)
                        .map(LlmMessage::content)
                        .collect(Collectors.joining("\n\n"));
        MessageCreateParams.Builder b =
                MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .system(system)
                        .outputConfig(
                                OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                        .putAdditionalBodyProperty(
                                "thinking", JsonValue.from(Map.of("type", "between_tools")))
                        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                        .putAdditionalHeader("anthropic-beta", FALLBACK_BETA);
        for (LlmMessage m : messages) {
            if (m.role() == LlmMessage.Role.user) {
                b.addUserMessage(m.content());
            } else if (m.role() == LlmMessage.Role.assistant) {
                b.addAssistantMessage(m.content());
            }
        }
        return b.build();
    }

    @Override
    public LlmUsage stream(List<LlmMessage> messages, Consumer<String> onDelta) {
        long input = 0;
        long output = 0;
        try (StreamResponse<RawMessageStreamEvent> response =
                client.messages().createStreaming(buildParams(messages))) {
            Iterator<RawMessageStreamEvent> it = response.stream().iterator();
            while (it.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("interrupted");
                }
                RawMessageStreamEvent event = it.next();
                if (event.messageStart().isPresent()) {
                    input = event.messageStart().get().message().usage().inputTokens();
                }
                event.contentBlockDelta()
                        .flatMap(d -> d.delta().text())
                        .ifPresent(t -> onDelta.accept(t.text()));
                if (event.messageDelta().isPresent()) {
                    var delta = event.messageDelta().get();
                    output = delta.usage().outputTokens();
                    if (delta.delta().stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                        throw new LlmException("refusal");
                    }
                }
            }
            return new LlmUsage((int) input, (int) output);
        } catch (RateLimitException e) {
            throw new LlmRateLimitedException("rate limited");
        } catch (AnthropicServiceException e) {
            // overloaded / 5xx / 4xx khác: lỗi dịch vụ (không kèm nội dung yêu cầu)
            throw new LlmException("provider status " + e.statusCode(), e);
        } catch (AnthropicIoException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("interrupted");
            }
            if (e.getCause() instanceof InterruptedIOException) {
                throw new LlmTimeoutException("provider timeout");
            }
            throw new LlmException("provider unreachable", e);
        }
    }
}
