package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.port.LlmPort.LlmMessage;
import com.chatvsdoc.api.port.LlmPort.LlmUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Hợp đồng mọi adapter {@link LlmPort} phải thỏa với một lần gọi bình thường. */
public abstract class LlmPortContract {

    protected abstract LlmPort create();

    protected static final List<LlmMessage> PROMPT =
            List.of(
                    new LlmMessage(LlmMessage.Role.system, "Chỉ trả lời dựa trên tài liệu."),
                    new LlmMessage(LlmMessage.Role.user, "Thời hạn thanh toán là bao lâu?"));

    @Test
    void streamsDeltasAndReportsUsage() {
        List<String> deltas = new ArrayList<>();
        LlmUsage usage = create().stream(PROMPT, deltas::add);
        assertThat(deltas).isNotEmpty().allSatisfy(d -> assertThat(d).isNotEmpty());
        assertThat(usage.inputTokens()).isPositive();
        assertThat(usage.outputTokens()).isPositive();
    }
}
