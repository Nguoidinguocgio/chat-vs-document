package com.chatvsdoc.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cấu hình ứng dụng, đọc từ biến môi trường qua application.yml (xem .env.example). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Min(1) int sessionIdleHours,
        @NotNull Path storageDir,
        @Min(1) int maxDocs,
        @Min(1) int maxFileMb,
        @Min(1) int maxPages,
        @Valid @NotNull Embedding embedding,
        @Valid @NotNull Llm llm,
        @Valid @NotNull Retrieval retrieval,
        boolean sessionCookieSecure,
        @NotBlank String appVersion,
        @Valid @NotNull Sse sse,
        @Valid @NotNull Processing processing,
        @Valid @NotNull Chat chat) {

    /** Hỏi đáp (T14 mục 3.4): giới hạn câu trả lời đồng thời. */
    public record Chat(
            @Min(1) int maxConcurrent,
            @Min(1) int perUser,
            @Min(0) int queueWaitSeconds,
            @Min(1) int pingSeconds) {}

    /** Kênh SSE {@code /api/events} (T09). */
    public record Sse(
            @Min(1) int heartbeatSeconds,
            @Min(1) int pingSeconds,
            @Min(1) int maxConnectionsPerUser,
            @Min(1) int bufferSize,
            @Min(1) int bufferMinutes) {}

    /** Hàng đợi và pipeline xử lý PDF (T07, T08). */
    public record Processing(
            @Min(1) int workers,
            @Min(1) int timeoutSeconds,
            @Min(1) int orphanMinutes,
            @Min(1) int embeddingBatchSize,
            @Min(0) long retryBackoffMs,
            @Min(1) int minPageChars) {}

    public record Embedding(
            @NotBlank String provider, String baseUrl, String model, @Min(1) int dim) {}

    public record Llm(
            @NotBlank String provider,
            String model,
            String apiKey,
            @Min(1) int firstTokenTimeoutS,
            @Min(1) int streamStallSeconds,
            @Min(64) long maxTokens) {

        /** Không để lộ khóa API khi cấu hình bị in ra log. */
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", model=" + model + ", apiKey=***]";
        }
    }

    public record Retrieval(
            @Min(1) int topK, @DecimalMin("0.0") @DecimalMax("1.0") double minScore) {}
}
