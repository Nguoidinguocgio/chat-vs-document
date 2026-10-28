package com.chatvsdoc.api.common;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi dùng chung (docs/implement/README.md mục 3). Mã riêng của từng nhóm chức năng được thêm ở
 * task tương ứng.
 */
public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "errors.unauthenticated"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "errors.session_expired"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "errors.forbidden"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "errors.password_change_required"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "errors.not_found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "errors.method_not_allowed"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "errors.payload_too_large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "errors.unsupported_media_type"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "errors.validation"),
    // Đăng nhập (T04)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "auth.invalid_credentials"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "auth.locked"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "auth.disabled"),
    // Tài liệu (T07)
    NOT_PDF(HttpStatus.BAD_REQUEST, "documents.not_pdf"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "documents.too_large"),
    DOCUMENT_LIMIT_REACHED(HttpStatus.CONFLICT, "documents.limit_reached"),
    NOT_RETRYABLE(HttpStatus.CONFLICT, "documents.not_retryable"),
    // Hỏi đáp (T14)
    QUESTION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "chat.question_invalid"),
    NO_READY_DOCUMENTS(HttpStatus.CONFLICT, "chat.empty_workspace"),
    ANSWER_IN_PROGRESS(HttpStatus.CONFLICT, "chat.answer_in_progress"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "errors.generic");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }
}
