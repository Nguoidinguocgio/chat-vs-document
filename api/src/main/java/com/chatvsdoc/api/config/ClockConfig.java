package com.chatvsdoc.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Đồng hồ dùng chung; test thay bằng đồng hồ giả để kiểm tra hết hạn phiên và khóa tạm. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
