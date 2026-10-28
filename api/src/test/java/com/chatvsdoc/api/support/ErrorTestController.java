package com.chatvsdoc.api.support;

import com.chatvsdoc.api.common.AppException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint chỉ có trong test, mỗi endpoint gây một loại lỗi. */
@TestComponent
@RestController
public class ErrorTestController {

    /** Body mẫu để kiểm tra Bean Validation. */
    public record Body(@NotBlank String displayName, @Size(max = 3) String code) {}

    @GetMapping("/api/test/app-error")
    void appError() {
        throw new AppException(
                "DOCUMENT_LIMIT_REACHED",
                HttpStatus.CONFLICT,
                "documents.limit_reached",
                Map.of("limit", 20));
    }

    @GetMapping("/api/test/boom")
    void boom() {
        throw new IllegalStateException("unexpected");
    }

    @GetMapping("/api/test/client-gone")
    void clientGone() throws java.io.IOException {
        throw new java.io.IOException("Broken pipe");
    }

    @GetMapping("/api/test/forbidden")
    void forbidden() {
        throw new AccessDeniedException("no");
    }

    @PostMapping("/api/test/validate")
    Map<String, String> validate(@Valid @RequestBody Body body) {
        return Map.of("ok", "yes");
    }
}
