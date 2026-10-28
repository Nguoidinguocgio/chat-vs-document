package com.chatvsdoc.api.port;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sinh câu trả lời dạng luồng; adapter thật (Anthropic Claude) do T13 hiện thực.
 *
 * <p>Hủy bằng cách interrupt luồng đang gọi: adapter dừng sớm nhất có thể và ném {@link
 * java.util.concurrent.CancellationException}.
 */
public interface LlmPort {

    /**
     * Gửi {@code messages}, gọi {@code onDelta} cho từng đoạn chữ theo thứ tự.
     *
     * @throws LlmTimeoutException quá thời gian chờ token đầu / luồng bị treo
     * @throws LlmRateLimitedException nhà cung cấp giới hạn tần suất
     * @throws LlmException lỗi khác của nhà cung cấp
     */
    LlmUsage stream(List<LlmMessage> messages, Consumer<String> onDelta);

    /** Một tin nhắn gửi LLM. */
    record LlmMessage(Role role, String content) {
        public enum Role {
            system,
            user,
            assistant
        }
    }

    /** Số token đã dùng cho một lần gọi. */
    record LlmUsage(int inputTokens, int outputTokens) {}

    /** Lỗi chung của nhà cung cấp LLM. */
    class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Quá thời gian chờ. */
    class LlmTimeoutException extends LlmException {
        public LlmTimeoutException(String message) {
            super(message);
        }
    }

    /** Bị giới hạn tần suất. */
    class LlmRateLimitedException extends LlmException {
        public LlmRateLimitedException(String message) {
            super(message);
        }
    }
}
