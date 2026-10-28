package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.chatvsdoc.api.port.fake.FakeEmbedding;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeEmbeddingTest extends EmbeddingPortContract {

    @Override
    protected EmbeddingPort create() {
        return new FakeEmbedding(1024);
    }

    @Test
    void vectorsAreUnitLength() {
        float[] v = port.embed(List.of("Điều khoản thanh toán")).get(0);
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        assertThat(norm).isCloseTo(1.0, offset(1e-5));
    }
}
