package com.chatvsdoc.api.config;

import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.auth.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc cookie {@code sid}, nạp {@link AuthUser} vào SecurityContext (vai trò → {@code ROLE_MEMBER}
 * …). Phiên hết hạn/thu hồi không chặn ở đây: ghi lý do vào thuộc tính yêu cầu để entry point trả
 * {@code SESSION_EXPIRED} khi đường dẫn cần đăng nhập (trang đăng nhập vẫn dùng được).
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "sid";
    public static final String AUTH_ERROR_ATTR = SessionAuthFilter.class.getName() + ".error";

    private final SessionService sessions;

    public SessionAuthFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            SessionService.Resolution r = sessions.resolve(token);
            if (r.user() != null) {
                AuthUser u = r.user();
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                u,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_"
                                                        + u.role()
                                                                .name()
                                                                .toUpperCase(Locale.ROOT))));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                request.setAttribute(AUTH_ERROR_ATTR, r.error());
            }
        }
        chain.doFilter(request, response);
    }

    static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    /** Mã lỗi 401 phù hợp cho yêu cầu chưa xác thực. */
    public static ErrorCode unauthenticatedCode(HttpServletRequest request) {
        return request.getAttribute(AUTH_ERROR_ATTR) instanceof ErrorCode e
                ? e
                : ErrorCode.UNAUTHENTICATED;
    }
}
