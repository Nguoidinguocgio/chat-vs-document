package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.messages.MessageCreateParams;
import com.chatvsdoc.api.port.LlmPort.LlmMessage;
import com.chatvsdoc.api.port.adapter.LlmAnthropicAdapter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Adapter Anthropic: kiểm tra yêu cầu dựng đúng (không gọi mạng). Đồng thời nạp các lớp của SDK để
 * phát hiện sớm xung đột thư viện (Kotlin/Jackson/OkHttp) với Spring Boot.
 */
class LlmAnthropicAdapterTest {

    @Test
    void buildsStreamingRequestWithBetweenToolsThinkingAndServerFallbackAndNoSampling() {
        LlmAnthropicAdapter a =
                new LlmAnthropicAdapter(
                        "sk-test", "claude-sonnet-5-5", 1500, Duration.ofSeconds(5));
        MessageCreateParams p =
                a.buildParams(
                        List.of(
                                new LlmMessage(LlmMessage.Role.system, "quy tắc"),
                                new LlmMessage(LlmMessage.Role.user, "Câu hỏi: x")));
        assertThat(p.model().asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(p.maxTokens()).isEqualTo(1500);
        String body = p._additionalBodyProperties().toString();
        assertThat(body).contains("between_tools").contains("fallbacks");
        assertThat(p._additionalHeaders().values("anthropic-beta"))
                .contains("server-side-fallback-2026-07-01");
        // SDK 3.x không còn setter temperature/top_p/top_k; bảo đảm không ai thêm qua body bổ sung.
        assertThat(body)
                .doesNotContain("temperature")
                .doesNotContain("top_p")
                .doesNotContain("top_k");
        assertThat(p.system()).isPresent();
        assertThat(p.messages()).hasSize(1);
    }

    @Test
    void requiresApiKey() {
        assertThatThrownBy(() -> new LlmAnthropicAdapter("", "m", 100, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY");
    }
}
