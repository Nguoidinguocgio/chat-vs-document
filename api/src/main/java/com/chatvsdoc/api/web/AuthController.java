package com.chatvsdoc.api.web;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.config.SessionAuthFilter;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.service.auth.AuthService;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.auth.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Đăng nhập, đăng xuất, người dùng hiện tại, đổi mật khẩu (T04 mục 3.1). */
@RestController
public class AuthController {

    /** {@code CurrentUser} trong hợp đồng API. */
    public record CurrentUser(
            UUID id, String email, String displayName, UserRole role, boolean mustChangePassword) {
        static CurrentUser of(AuthUser u) {
            return new CurrentUser(
                    u.id(), u.email(), u.displayName(), u.role(), u.mustChangePassword());
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 1024) String password) {}

    public record ChangePasswordRequest(
            @Size(max = 1024) String currentPassword,
            @NotBlank @Size(max = 1024) String newPassword) {}

    private final AuthService auth;
    private final SessionService sessions;
    private final boolean secureCookie;

    public AuthController(AuthService auth, SessionService sessions, AppProperties props) {
        this.auth = auth;
        this.sessions = sessions;
        this.secureCookie = props.sessionCookieSecure();
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<CurrentUser> login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult r = auth.login(req.email(), req.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(r.token(), -1).toString())
                .body(CurrentUser.of(r.user()));
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser me) {
        if (me != null) {
            sessions.delete(me.sessionId());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .build();
    }

    @GetMapping("/api/me")
    CurrentUser me(@AuthenticationPrincipal AuthUser me) {
        if (me == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return CurrentUser.of(me);
    }

    @PostMapping("/api/auth/change-password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(me, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    // Cookie phiên (không Max-Age) — HttpOnly, SameSite=Strict, Secure trừ khi tắt cho dev HTTP.
    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(SessionAuthFilter.COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
