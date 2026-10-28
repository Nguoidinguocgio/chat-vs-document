package com.chatvsdoc.api.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có chủ đích; {@link GlobalExceptionHandler} chuyển thành định dạng lỗi chuẩn. Không
 * đưa nội dung nghiệp vụ (câu hỏi, văn bản tài liệu) vào {@code details}.
 */
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final String messageKey;
    private final Map<String, Object> details;

    public AppException(
            String code, HttpStatus status, String messageKey, Map<String, Object> details) {
        super(code);
        this.code = code;
        this.status = status;
        this.messageKey = messageKey;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public AppException(ErrorCode code) {
        this(code, Map.of());
    }

    public AppException(ErrorCode code, Map<String, Object> details) {
        this(code.name(), code.status(), code.messageKey(), details);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, Object> details() {
        return details;
    }
}
