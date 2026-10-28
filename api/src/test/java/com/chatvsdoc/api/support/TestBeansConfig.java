package com.chatvsdoc.api.support;

import com.chatvsdoc.api.port.fake.FakeLlm;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay đồng hồ, embedding và LLM bằng bản điều khiển được trong test tích hợp. */
@TestConfiguration(proxyBeanMethods = false)
public class TestBeansConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    SwitchableEmbedding switchableEmbedding() {
        return new SwitchableEmbedding();
    }

    /** LLM giả theo từ khóa, mảnh cách nhau 5 ms; "cham" im lặng 4 s (hạn chờ test là 2 s). */
    @Bean
    @Primary
    FakeLlm fakeLlm() {
        return new FakeLlm(Duration.ofMillis(5), Duration.ofSeconds(4));
    }
}
