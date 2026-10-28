package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hợp đồng mọi adapter {@link EmbeddingPort} phải thỏa. */
public abstract class EmbeddingPortContract {

    protected EmbeddingPort port;

    protected abstract EmbeddingPort create();

    @BeforeEach
    void setUp() {
        port = create();
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    @Test
    void oneVectorPerTextInOrderWithDeclaredDimension() {
        List<float[]> v = port.embed(List.of("Điều khoản thanh toán", "Bảo hành", ""));
        assertThat(v).hasSize(3);
        assertThat(v).allSatisfy(x -> assertThat(x).hasSize(port.dimension()));
        assertThat(port.embed(List.of())).isEmpty();
    }

    @Test
    void deterministic() {
        assertThat(port.embed(List.of("thời hạn thanh toán")).get(0))
                .containsExactly(port.embed(List.of("thời hạn thanh toán")).get(0));
    }

    @Test
    void relatedTextIsCloserThanUnrelated() {
        List<float[]> v =
                port.embed(
                        List.of(
                                "thời hạn thanh toán hóa đơn",
                                "hóa đơn phải thanh toán trong 30 ngày",
                                "nhiệt độ bảo quản thiết bị"));
        assertThat(cosine(v.get(0), v.get(1))).isGreaterThan(cosine(v.get(0), v.get(2)));
    }
}
