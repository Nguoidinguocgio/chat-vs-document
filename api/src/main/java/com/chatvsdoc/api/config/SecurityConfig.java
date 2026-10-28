package com.chatvsdoc.api.config;

import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.common.ErrorResponseWriter;
import com.chatvsdoc.api.service.auth.SessionService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Xác thực bằng cookie {@code sid} ({@link SessionAuthFilter}); phân quyền theo đường dẫn và
 * {@code @PreAuthorize("hasRole('ADMIN')")}. CSRF mặc định tắt (thay bằng {@code
 * CsrfHeaderFilter}), header do {@code SecurityHeadersFilter} đặt. Mọi lỗi 401/403 trả định dạng
 * chuẩn.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ErrorResponseWriter errors, SessionService sessions)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new SessionAuthFilter(sessions), AnonymousAuthenticationFilter.class)
                .addFilterAfter(
                        new PasswordChangeRequiredFilter(errors),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(
                        auth ->
                                // SSE/async và trang lỗi chạy lại qua dispatcher; đã xác thực ở
                                // lượt REQUEST.
                                auth.dispatcherTypeMatchers(
                                                DispatcherType.ASYNC, DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers("/healthz", "/readyz", "/error")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/logout")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        errors.write(
                                                                res,
                                                                SessionAuthFilter
                                                                        .unauthenticatedCode(req)))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        errors.write(res, ErrorCode.FORBIDDEN)));
        return http.build();
    }
}
