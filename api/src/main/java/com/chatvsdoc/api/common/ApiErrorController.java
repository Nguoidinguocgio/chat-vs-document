package com.chatvsdoc.api.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thay trang lỗi mặc định của Spring Boot ({@code /error}) để lỗi phát sinh ngoài Spring MVC (ví dụ
 * trong container servlet) vẫn theo định dạng chuẩn.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status instanceof Integer i ? i : 500;
        ErrorCode errorCode =
                switch (code) {
                    case 401 -> ErrorCode.UNAUTHENTICATED;
                    case 403 -> ErrorCode.FORBIDDEN;
                    case 404 -> ErrorCode.NOT_FOUND;
                    case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                    case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
                    case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                    case 400, 422 -> ErrorCode.VALIDATION_ERROR;
                    default -> ErrorCode.INTERNAL_ERROR;
                };
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }
}
