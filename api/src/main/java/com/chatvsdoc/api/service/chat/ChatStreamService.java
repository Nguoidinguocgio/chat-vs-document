package com.chatvsdoc.api.service.chat;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.domain.entity.Conversation;
import com.chatvsdoc.api.port.fake.FakeLlm;
import com.chatvsdoc.api.service.analytics.AnalyticsService;
import com.chatvsdoc.api.service.answer.AnswerEvent;
import com.chatvsdoc.api.service.answer.AnswerService;
import com.chatvsdoc.api.service.answer.LangDetector;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.retrieval.ContextBlock;
import com.chatvsdoc.api.service.retrieval.Retriever;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hỏi đáp qua SSE (T14 mục 3.2): kiểm tra trước khi mở luồng (lỗi trả JSON chuẩn), rồi {@code meta
 * → status → delta… → citation… → done|error}. Luôn kết thúc bằng đúng một {@code done}/{@code
 * error}; client ngắt giữa chừng → hủy sinh, {@code status=stopped}, giữ phần đã có. Không ghi nội
 * dung câu hỏi/câu trả lời vào log hay analytics.
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    public static final int MAX_QUESTION = 2000;
    static final long FLUSH_INTERVAL_MS = 1000;

    private final ConversationService conversations;
    private final Retriever retriever;
    private final AnswerService answers;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AppProperties.Chat cfg;
    private final Semaphore global;
    private final Map<UUID, AtomicInteger> perUser = new ConcurrentHashMap<>();
    private final Set<UUID> activeConversations = ConcurrentHashMap.newKeySet();
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService pinger =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    public ChatStreamService(
            ConversationService conversations,
            Retriever retriever,
            AnswerService answers,
            AnalyticsService analytics,
            JdbcTemplate jdbc,
            Clock clock,
            AppProperties props) {
        this.conversations = conversations;
        this.retriever = retriever;
        this.answers = answers;
        this.analytics = analytics;
        this.jdbc = jdbc;
        this.clock = clock;
        this.cfg = props.chat();
        this.global = new Semaphore(cfg.maxConcurrent(), true);
    }

    /** Câu trả lời của tiến trình trước bị bỏ dở khi khởi động lại → {@code failed}. */
    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedStreams() {
        int n =
                jdbc.update(
                        "UPDATE messages SET status = 'failed', error_code = 'llm_error',"
                                + " completed_at = now() WHERE status = 'streaming'");
        if (n > 0) {
            log.info("orphaned_answers_failed count={}", n);
        }
    }

    /** Số tác vụ sinh đang chạy (test kiểm tra không rò rỉ). */
    public int activeStreams() {
        return workers.size();
    }

    public SseEmitter ask(AuthUser me, UUID conversationId, String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION) {
            throw new AppException(ErrorCode.QUESTION_INVALID);
        }
        Conversation conv = conversations.requireOwned(me, conversationId);
        Integer ready =
                jdbc.queryForObject(
                        "SELECT count(*) FROM documents WHERE status = 'ready' AND deleted_at IS NULL",
                        Integer.class);
        if (ready == null || ready == 0) {
            throw new AppException(ErrorCode.NO_READY_DOCUMENTS);
        }
        Integer streaming =
                jdbc.queryForObject(
                        "SELECT count(*) FROM messages WHERE conversation_id = ? AND status = 'streaming'",
                        Integer.class,
                        conv.getId());
        if ((streaming != null && streaming > 0) || !activeConversations.add(conv.getId())) {
            throw new AppException(ErrorCode.ANSWER_IN_PROGRESS);
        }
        AtomicInteger mine = perUser.computeIfAbsent(me.id(), k -> new AtomicInteger());
        if (mine.incrementAndGet() > cfg.perUser()) {
            mine.decrementAndGet();
            activeConversations.remove(conv.getId());
            throw new AppException(ErrorCode.ANSWER_IN_PROGRESS);
        }
        String lang = LangDetector.detect(question);
        Timestamp now = Timestamp.from(clock.instant());
        UUID messageId = UUID.randomUUID();
        Integer turn =
                jdbc.queryForObject(
                        "SELECT count(*) FROM messages WHERE conversation_id = ?",
                        Integer.class,
                        conv.getId());
        jdbc.update(
                "INSERT INTO messages (id, conversation_id, question, lang, status, created_at)"
                        + " VALUES (?, ?, ?, ?, 'streaming', ?)",
                messageId,
                conv.getId(),
                question,
                lang,
                now);
        conversations.touch(conv.getId(), question, now);
        analytics.emit(
                "chat_question_submitted",
                me.id(),
                me.sessionId(),
                Map.of(
                        "conversation_id",
                        conv.getId().toString(),
                        "turn_index",
                        Objects.requireNonNullElse(turn, 0),
                        "question_length",
                        question.length(),
                        "question_lang",
                        lang,
                        "ready_document_count",
                        ready));

        Stream s = new Stream(me, conv.getId(), messageId, question, lang, mine);
        Thread worker = Thread.ofVirtual().name("answer-" + messageId).unstarted(s::run);
        s.worker = worker;
        s.emitter.onCompletion(s::clientGone);
        s.emitter.onTimeout(s::clientGone);
        s.emitter.onError(e -> s.clientGone());
        workers.add(worker);
        worker.start();
        return s.emitter;
    }

    /** Client đã ngắt (gửi thất bại). */
    private static final class ClientGone extends RuntimeException {
        ClientGone() {
            super("client gone", null, false, false);
        }
    }

    /** Một lượt trả lời đang chạy. */
    private final class Stream {
        final AuthUser me;
        final UUID conversationId;
        final UUID messageId;
        final String question;
        final String lang;
        final AtomicInteger userCount;
        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        final StringBuilder answer = new StringBuilder();
        final Set<UUID> citedDocs = new HashSet<>();
        Thread worker;
        volatile boolean finished;
        int citations;
        long lastFlush = System.nanoTime();

        Stream(AuthUser me, UUID conv, UUID msg, String q, String lang, AtomicInteger userCount) {
            this.me = me;
            this.conversationId = conv;
            this.messageId = msg;
            this.question = q;
            this.lang = lang;
            this.userCount = userCount;
        }

        void clientGone() {
            if (!finished && worker != null) {
                worker.interrupt();
            }
        }

        synchronized void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                throw new ClientGone();
            }
        }

        synchronized void ping() {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                clientGone();
            }
        }

        void run() {
            ScheduledFuture<?> ping =
                    pinger.scheduleAtFixedRate(
                            this::ping, cfg.pingSeconds(), cfg.pingSeconds(), TimeUnit.SECONDS);
            boolean acquired = false;
            try {
                send("meta", Map.of("message_id", messageId, "lang", lang));
                acquired = global.tryAcquire(cfg.queueWaitSeconds(), TimeUnit.SECONDS);
                if (!acquired) {
                    fail("rate_limited");
                    return;
                }
                send("status", Map.of("phase", "retrieving"));
                List<ContextBlock> blocks = retriever.search(question);
                if (blocks.isEmpty()) {
                    // Không có nguồn đủ liên quan: không gọi LLM.
                    complete(
                            new AnswerEvent.Done(
                                    AnswerType.not_found, null, false, null, 0, null, null));
                    return;
                }
                answers.stream(question, blocks, lang, this::onEvent);
            } catch (FakeLlm.SimulatedConnectionDrop drop) {
                // Chỉ LLM giả: đóng luồng đột ngột, không done/error — client thấy "Gián đoạn".
                saveAnswer("failed", "llm_error");
            } catch (InterruptedException | ClientGone e) {
                saveAnswer("stopped", null);
            } catch (RuntimeException e) {
                log.error("answer_stream_error message_id={}", messageId, e);
                try {
                    fail("llm_error");
                } catch (ClientGone gone) {
                    saveAnswer("stopped", null);
                }
            } finally {
                finished = true;
                ping.cancel(false);
                if (acquired) {
                    global.release();
                }
                userCount.decrementAndGet();
                activeConversations.remove(conversationId);
                workers.remove(Thread.currentThread());
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // client đã đi
                }
            }
        }

        void onEvent(AnswerEvent e) {
            switch (e) {
                case AnswerEvent.Delta d -> {
                    answer.append(d.text());
                    send("delta", Map.of("text", d.text()));
                    if (System.nanoTime() - lastFlush > FLUSH_INTERVAL_MS * 1_000_000) {
                        jdbc.update(
                                "UPDATE messages SET answer = ? WHERE id = ?",
                                answer.toString(),
                                messageId);
                        lastFlush = System.nanoTime();
                    }
                }
                case AnswerEvent.Citation c -> {
                    ContextBlock b = c.block();
                    jdbc.update(
                            "INSERT INTO citations (message_id, idx, document_id, page, chunk_id)"
                                    + " VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                            messageId,
                            c.index(),
                            b.documentId(),
                            b.page(),
                            b.chunkId());
                    citations++;
                    citedDocs.add(b.documentId());
                    send(
                            "citation",
                            Map.of(
                                    "index", c.index(),
                                    "document_id", b.documentId(),
                                    "document_name", b.documentName(),
                                    "page", b.page(),
                                    "document_deleted", false));
                }
                case AnswerEvent.Done d -> complete(d);
                case AnswerEvent.Failed f -> fail(f.code());
            }
        }

        void complete(AnswerEvent.Done d) {
            jdbc.update(
                    """
                    UPDATE messages SET status = 'completed', answer = ?, answer_type = ?::answer_type,
                      missing = ?, completed_at = ?, ttft_ms = ?, total_ms = ?,
                      llm_input_tokens = ?, llm_output_tokens = ?
                    WHERE id = ?
                    """,
                    answer.toString(),
                    d.answerType().name(),
                    d.missing(),
                    Timestamp.from(clock.instant()),
                    d.ttftMs(),
                    d.totalMs(),
                    d.inputTokens(),
                    d.outputTokens(),
                    messageId);
            touchConversation();
            Map<String, Object> props = new HashMap<>();
            props.put("question_id", messageId.toString());
            props.put("total_ms", d.totalMs());
            props.put("answer_type", d.answerType().name());
            props.put("citation_count", citations);
            props.put("distinct_document_count", citedDocs.size());
            if (d.inputTokens() != null) {
                props.put("llm_input_tokens", d.inputTokens());
            }
            if (d.outputTokens() != null) {
                props.put("llm_output_tokens", d.outputTokens());
            }
            analytics.emit("chat_answer_completed", me.id(), me.sessionId(), props);
            Map<String, Object> done = new HashMap<>();
            done.put("answer_type", d.answerType().name());
            done.put("missing", d.missing());
            send("done", done);
        }

        void fail(String code) {
            saveAnswer("failed", code);
            analytics.emit(
                    "chat_answer_failed",
                    me.id(),
                    me.sessionId(),
                    Map.of("question_id", messageId.toString(), "error_code", code));
            send("error", Map.of("code", code));
        }

        void saveAnswer(String status, String errorCode) {
            jdbc.update(
                    "UPDATE messages SET status = ?::message_status, answer = ?, error_code = ?,"
                            + " completed_at = ? WHERE id = ? AND status = 'streaming'",
                    status,
                    answer.toString(),
                    errorCode,
                    Timestamp.from(clock.instant()),
                    messageId);
            touchConversation();
        }

        void touchConversation() {
            jdbc.update(
                    "UPDATE conversations SET last_activity_at = ? WHERE id = ?",
                    Timestamp.from(clock.instant()),
                    conversationId);
        }
    }

    @PreDestroy
    void shutdown() {
        pinger.shutdownNow();
        workers.forEach(Thread::interrupt);
    }
}
