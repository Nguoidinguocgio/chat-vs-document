package com.chatvsdoc.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test tích hợp: toàn bộ ứng dụng trên cổng ngẫu nhiên (upload luồng, SSE cần HTTP thật) + MockMvc
 * + Postgres thật (Testcontainers), profile {@code test}. Mọi test dùng chung một
 * context/container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({
    TestcontainersConfig.class,
    TestBeansConfig.class,
    Fixtures.class,
    Corpus.class,
    ErrorTestController.class
})
@ActiveProfiles("test")
public @interface IntegrationTest {}
