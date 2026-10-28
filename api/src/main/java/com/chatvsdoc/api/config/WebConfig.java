package com.chatvsdoc.api.config;

import com.chatvsdoc.api.common.AccessLogFilter;
import com.chatvsdoc.api.common.AppVersionFilter;
import com.chatvsdoc.api.common.CsrfHeaderFilter;
import com.chatvsdoc.api.common.ErrorResponseWriter;
import com.chatvsdoc.api.common.RequestIdFilter;
import com.chatvsdoc.api.common.SecurityHeadersFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Thứ tự filter tường minh, tất cả chạy trước chuỗi Spring Security (order -100): request ID →
 * header bảo mật → log truy cập → CSRF nhẹ.
 */
@Configuration
public class WebConfig {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        return register(new RequestIdFilter(), 0);
    }

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        return register(new SecurityHeadersFilter(), 1);
    }

    @Bean
    FilterRegistrationBean<AppVersionFilter> appVersionFilter(AppProperties props) {
        return register(new AppVersionFilter(props.appVersion()), 1);
    }

    @Bean
    FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        return register(new AccessLogFilter(), 2);
    }

    @Bean
    FilterRegistrationBean<CsrfHeaderFilter> csrfHeaderFilter(ErrorResponseWriter errors) {
        return register(new CsrfHeaderFilter(errors), 3);
    }

    private static <F extends Filter> FilterRegistrationBean<F> register(F filter, int position) {
        FilterRegistrationBean<F> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + position);
        return bean;
    }
}
