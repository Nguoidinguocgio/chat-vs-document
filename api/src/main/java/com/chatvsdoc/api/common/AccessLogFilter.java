package com.chatvsdoc.api.common;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Một dòng log cho mỗi yêu cầu: chỉ phương thức, đường dẫn (không query string), status, độ trễ.
 * Không đọc/ghi body hay header nhạy cảm.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "request",
                    kv("method", request.getMethod()),
                    kv("route", request.getRequestURI()),
                    kv("status", response.getStatus()),
                    kv("duration_ms", ms));
        }
    }
}
