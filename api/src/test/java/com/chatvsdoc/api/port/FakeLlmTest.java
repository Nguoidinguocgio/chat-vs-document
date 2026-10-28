package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatvsdoc.api.port.LlmPort.LlmRateLimitedException;
import com.chatvsdoc.api.port.LlmPort.LlmTimeoutException;
import com.chatvsdoc.api.port.fake.FakeLlm;
import com.chatvsdoc.api.port.fake.FakeLlm.Script;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FakeLlmTest extends LlmPortContract {

    @Override
    protected LlmPort create() {
        return new FakeLlm();
    }

    @Test
    void emitsScriptedDeltasInOrder() {
        List<String> out = new ArrayList<>();
        new FakeLlm(Script.of("A", "B", "C")).stream(PROMPT, out::add);
        assertThat(out).containsExactly("A", "B", "C");
    }

    @Test
    void scriptedFailuresAfterSomeDeltas() {
        List<String> out = new ArrayList<>();
        FakeLlm llm =
                new FakeLlm(
                        Script.of("A", "B").failingAfter(1, new LlmRateLimitedException("429")));
        assertThatThrownBy(() -> llm.stream(PROMPT, out::add))
                .isInstanceOf(LlmRateLimitedException.class);
        assertThat(out).containsExactly("A");

        llm.setScript(Script.of().failingAfter(0, new LlmTimeoutException("ttft")));
        assertThatThrownBy(() -> llm.stream(PROMPT, out::add))
                .isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void interruptCancelsStream() throws Exception {
        FakeLlm llm = new FakeLlm(Script.of("A", "B", "C", "D").withDelay(Duration.ofMillis(200)));
        List<String> out = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        llm.stream(PROMPT, out::add);
                                    } catch (RuntimeException e) {
                                        error.set(e);
                                    }
                                });
        Thread.sleep(300);
        t.interrupt();
        t.join(2000);
        assertThat(error.get()).isInstanceOf(CancellationException.class);
        assertThat(out.size()).isLessThan(4);
    }
}
