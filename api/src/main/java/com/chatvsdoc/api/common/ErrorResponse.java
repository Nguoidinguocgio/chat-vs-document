package com.chatvsdoc.api.common;

import java.util.Map;

/** Định dạng lỗi duy nhất: {@code {"error":{"code","message_key","details"}}}. */
public record ErrorResponse(Body error) {

    public record Body(String code, String messageKey, Map<String, Object> details) {}

    public static ErrorResponse of(String code, String messageKey, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, messageKey, details == null ? Map.of() : details));
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code.name(), code.messageKey(), Map.of());
    }

    public static ErrorResponse of(AppException ex) {
        return of(ex.code(), ex.messageKey(), ex.details());
    }
}
