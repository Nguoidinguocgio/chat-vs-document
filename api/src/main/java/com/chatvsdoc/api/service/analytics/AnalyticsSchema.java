package com.chatvsdoc.api.service.analytics;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Danh sách trắng tên sự kiện và thuộc tính (PRD §6.2). Thuộc tính chỉ là số, boolean, ID hoặc giá
 * trị enum — không bao giờ có văn bản tự do (tên file, câu hỏi, nội dung tài liệu).
 */
public final class AnalyticsSchema {

    private AnalyticsSchema() {}

    /** Kiểu cho phép của một thuộc tính. */
    public sealed interface PropType {
        boolean accepts(Object value);
    }

    /** Số không âm (độ trễ, kích thước, số đếm). */
    public record Num() implements PropType {
        @Override
        public boolean accepts(Object v) {
            return v instanceof Number n
                    && Double.isFinite(n.doubleValue())
                    && n.doubleValue() >= 0;
        }
    }

    /** Giá trị boolean. */
    public record Bool() implements PropType {
        @Override
        public boolean accepts(Object v) {
            return v instanceof Boolean;
        }
    }

    /** UUID hoặc mã ngắn chỉ gồm chữ thường/số/{@code -_}, ≤ 64 ký tự. */
    public record Id() implements PropType {
        private static final Pattern ID = Pattern.compile("[0-9a-zA-Z_-]{1,64}");

        @Override
        public boolean accepts(Object v) {
            return v instanceof String s && ID.matcher(s).matches();
        }
    }

    /** Một trong các giá trị liệt kê. */
    public record Enum(Set<String> values) implements PropType {
        @Override
        public boolean accepts(Object v) {
            return v instanceof String s && values.contains(s);
        }
    }

    private static final PropType NUM = new Num();
    private static final PropType BOOL = new Bool();
    private static final PropType ID = new Id();

    private static PropType oneOf(String... values) {
        return new Enum(Set.of(values));
    }

    private static final PropType OUTCOME =
            oneOf(
                    "answered",
                    "partial",
                    "not_found",
                    "stopped",
                    "failed",
                    "timeout",
                    "llm_error",
                    "rate_limited",
                    "network");
    private static final PropType NETWORK_CONTEXT = oneOf("upload", "streaming", "idle");

    /** Mọi sự kiện được ghi, kể cả do máy chủ phát. */
    public static final Map<String, Map<String, PropType>> EVENTS =
            Map.ofEntries(
                    Map.entry("auth_login_succeeded", Map.of("is_first_login", BOOL)), // EV-001
                    Map.entry(
                            "auth_login_failed",
                            Map.of("reason", oneOf("invalid_credentials", "locked", "disabled"))),
                    Map.entry(
                            "document_upload_started", // EV-010
                            Map.of("file_size_bytes", NUM, "batch_size", NUM)),
                    Map.entry(
                            "document_upload_rejected", // EV-011
                            Map.of(
                                    "reason",
                                    oneOf("not_pdf", "too_large", "limit_reached", "duplicate"))),
                    Map.entry(
                            "document_processing_completed", // EV-012
                            Map.of(
                                    "document_id", ID,
                                    "page_count", NUM,
                                    "processing_ms", NUM,
                                    "unreadable_page_count", NUM)),
                    Map.entry(
                            "document_processing_failed", // EV-013
                            Map.of(
                                    "document_id",
                                    ID,
                                    "reason",
                                    oneOf(
                                            "too_many_pages",
                                            "password_protected",
                                            "scanned_pdf",
                                            "corrupted",
                                            "system_error"))),
                    Map.entry(
                            "chat_question_submitted", // EV-020
                            Map.of(
                                    "conversation_id", ID,
                                    "turn_index", NUM,
                                    "question_length", NUM,
                                    "question_lang", oneOf("vi", "en"),
                                    "ready_document_count", NUM)),
                    Map.entry(
                            "chat_answer_first_token", // EV-021
                            Map.of("question_id", ID, "ttft_ms", NUM)),
                    Map.entry(
                            "chat_answer_stopped", // EV-022
                            Map.of("question_id", ID, "elapsed_ms", NUM)),
                    Map.entry(
                            "chat_answer_completed", // EV-023
                            Map.of(
                                    "question_id", ID,
                                    "total_ms", NUM,
                                    "answer_type", oneOf("answered", "not_found", "partial"),
                                    "citation_count", NUM,
                                    "distinct_document_count", NUM,
                                    "llm_input_tokens", NUM,
                                    "llm_output_tokens", NUM)),
                    Map.entry(
                            "chat_answer_failed", // EV-024
                            Map.of(
                                    "question_id",
                                    ID,
                                    "error_code",
                                    oneOf("timeout", "llm_error", "rate_limited", "network"))),
                    Map.entry(
                            "chat_answer_regenerated", // EV-025
                            Map.of("question_id", ID, "previous_outcome", OUTCOME)),
                    Map.entry(
                            "citation_clicked", // EV-030
                            Map.of(
                                    "question_id", ID,
                                    "document_id", ID,
                                    "page", NUM,
                                    "citation_index", NUM,
                                    "viewer_load_ms", NUM)),
                    Map.entry(
                            "client_network_lost", // EV-050
                            Map.of("context", NETWORK_CONTEXT, "duration_ms", NUM)),
                    Map.entry(
                            "client_network_restored",
                            Map.of("context", NETWORK_CONTEXT, "duration_ms", NUM)));

    /** Sự kiện client được phép gửi qua {@code POST /api/analytics/events}. */
    public static final Set<String> CLIENT_EVENTS =
            Set.of(
                    "chat_answer_first_token",
                    "chat_answer_stopped",
                    "chat_answer_regenerated",
                    "citation_clicked",
                    "client_network_lost",
                    "client_network_restored");

    /** {@code null} nếu hợp lệ, ngược lại mô tả ngắn (không chứa giá trị) để đưa vào details. */
    public static String violation(String name, Map<String, Object> props) {
        Map<String, PropType> allowed = EVENTS.get(name);
        if (allowed == null) {
            return "unknown_event";
        }
        for (Map.Entry<String, Object> e : props.entrySet()) {
            PropType type = allowed.get(e.getKey());
            if (type == null) {
                return "unknown_prop:" + e.getKey();
            }
            if (!type.accepts(e.getValue())) {
                return "invalid_value:" + e.getKey();
            }
        }
        return null;
    }
}
