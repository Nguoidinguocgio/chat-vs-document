package com.chatvsdoc.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** {@code X-App-Version} trên mọi phản hồi: web báo "Có phiên bản mới" khi giá trị đổi (T05). */
public class AppVersionFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-App-Version";

    private final String version;

    public AppVersionFilter(String version) {
        this.version = version;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, version);
        chain.doFilter(request, response);
    }
}
