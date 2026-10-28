package com.chatvsdoc.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF nhẹ: yêu cầu thay đổi dữ liệu phải kèm {@code X-Requested-With: fetch} (trình duyệt không
 * cho trang khác tự đặt header này mà không qua CORS). Kết hợp cookie phiên SameSite.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "fetch";
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ErrorResponseWriter errorWriter;

    public CsrfHeaderFilter(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (UNSAFE.contains(request.getMethod()) && !VALUE.equals(request.getHeader(HEADER))) {
            errorWriter.write(response, ErrorCode.FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
