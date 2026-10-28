package com.chatvsdoc.api.config;

import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.common.ErrorResponseWriter;
import com.chatvsdoc.api.service.auth.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tài khoản đang dùng mật khẩu tạm chỉ được gọi {@code /api/me}, đăng xuất và đổi mật khẩu; mọi API
 * khác → {@code 403 PASSWORD_CHANGE_REQUIRED} (AC-001.4).
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED =
            Set.of("/api/me", "/api/auth/logout", "/api/auth/change-password", "/api/auth/login");

    private final ErrorResponseWriter errors;

    public PasswordChangeRequiredFilter(ErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI();
        if (auth != null
                && auth.getPrincipal() instanceof AuthUser u
                && u.mustChangePassword()
                && path.startsWith("/api/")
                && !ALLOWED.contains(path)) {
            errors.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
