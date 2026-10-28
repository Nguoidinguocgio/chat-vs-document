package com.chatvsdoc.api.config;

import com.chatvsdoc.api.port.EmbeddingPort;
import com.chatvsdoc.api.port.LlmPort;
import com.chatvsdoc.api.port.StoragePort;
import com.chatvsdoc.api.port.adapter.LlmAnthropicAdapter;
import com.chatvsdoc.api.port.adapter.LocalFileStorage;
import com.chatvsdoc.api.port.adapter.OllamaEmbeddingAdapter;
import com.chatvsdoc.api.port.fake.FakeEmbedding;
import com.chatvsdoc.api.port.fake.FakeLlm;
import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Chọn adapter theo EMBEDDING_PROVIDER / LLM_PROVIDER. Bean {@code @Lazy} để ứng dụng vẫn khởi động
 * khi adapter thật chưa có (T08, T13); lỗi chỉ xuất hiện khi chức năng cần đến nó.
 */
@Configuration
public class PortConfig {

    @Bean
    StoragePort storagePort(AppProperties props) throws IOException {
        return new LocalFileStorage(props.storageDir());
    }

    @Bean
    @Lazy
    EmbeddingPort embeddingPort(AppProperties props) {
        AppProperties.Embedding e = props.embedding();
        return switch (e.provider()) {
            case "fake" -> new FakeEmbedding(e.dim());
            case "ollama" ->
                    new OllamaEmbeddingAdapter(
                            e.baseUrl(), e.model(), e.dim(), Duration.ofSeconds(120));
            default ->
                    throw new IllegalStateException(
                            "EMBEDDING_PROVIDER="
                                    + e.provider()
                                    + " không được hỗ trợ (ollama | fake)");
        };
    }

    @Bean
    @Lazy
    LlmPort llmPort(AppProperties props) {
        AppProperties.Llm l = props.llm();
        return switch (l.provider()) {
            case "fake" ->
                    new FakeLlm(Duration.ofMillis(30), Duration.ofSeconds(l.streamStallSeconds()));
            case "anthropic" ->
                    new LlmAnthropicAdapter(
                            l.apiKey(), l.model(), l.maxTokens(), Duration.ofSeconds(120));
            default ->
                    throw new IllegalStateException(
                            "LLM_PROVIDER="
                                    + l.provider()
                                    + " không được hỗ trợ (anthropic | fake)");
        };
    }
}
