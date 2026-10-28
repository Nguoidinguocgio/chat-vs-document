package com.chatvsdoc.api.port.fake;

import com.chatvsdoc.api.port.EmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Embedding giả, xác định (EMBEDDING_PROVIDER=fake): mỗi từ băm vào một chiều, vector chuẩn hóa L2.
 * Văn bản chung nhiều từ thì gần nhau theo cosine — đủ để test truy hồi mà không cần mô hình.
 */
public class FakeEmbedding implements EmbeddingPort {

    private final int dim;

    public FakeEmbedding(int dim) {
        if (dim < 8) {
            throw new IllegalArgumentException("dim must be >= 8");
        }
        this.dim = dim;
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embedOne(t));
        }
        return out;
    }

    private float[] embedOne(String text) {
        float[] v = new float[dim];
        String normalized =
                Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                CRC32 crc = new CRC32();
                crc.update(token.getBytes(StandardCharsets.UTF_8));
                v[(int) (crc.getValue() % dim)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            // Văn bản không có từ: vector đơn vị cố định để cosine vẫn xác định.
            v[0] = 1f;
            return v;
        }
        float inv = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < dim; i++) {
            v[i] *= inv;
        }
        return v;
    }
}
