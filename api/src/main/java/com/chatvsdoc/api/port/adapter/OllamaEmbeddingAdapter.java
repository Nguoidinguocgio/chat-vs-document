package com.chatvsdoc.api.port.adapter;

import com.chatvsdoc.api.port.EmbeddingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding qua Ollama ({@code POST /api/embed}), mô hình bge-m3: đa ngôn ngữ (Việt/Anh, truy hồi
 * chéo ngôn ngữ), 1024 chiều, không cần tiền tố query/passage. Vector được chuẩn hóa lại độ dài 1.
 */
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    /** Kích thước lô tối đa một lần gọi. */
    static final int MAX_BATCH = 32;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final URI endpoint;
    private final String model;
    private final int dim;
    private final Duration timeout;

    public OllamaEmbeddingAdapter(String baseUrl, String model, int dim, Duration timeout) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/embed");
        this.model = model;
        this.dim = dim;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            out.addAll(call(texts.subList(i, Math.min(texts.size(), i + MAX_BATCH))));
        }
        return out;
    }

    private List<float[]> call(List<String> batch) {
        HttpResponse<String> res;
        try {
            String body = json.writeValueAsString(Map.of("model", model, "input", batch));
            HttpRequest req =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmbeddingException("ollama unreachable", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("interrupted", false, e);
        }
        int status = res.statusCode();
        if (status == 429 || status >= 500) {
            throw new EmbeddingException("ollama status " + status, true, null);
        }
        if (status != 200) {
            throw new EmbeddingException("ollama status " + status, false, null);
        }
        try {
            JsonNode vectors = json.readTree(res.body()).path("embeddings");
            if (!vectors.isArray() || vectors.size() != batch.size()) {
                throw new EmbeddingException("unexpected ollama response", false, null);
            }
            List<float[]> out = new ArrayList<>(batch.size());
            for (JsonNode v : vectors) {
                if (v.size() != dim) {
                    throw new EmbeddingException(
                            "dimension " + v.size() + " != EMBEDDING_DIM " + dim, false, null);
                }
                out.add(normalize(v));
            }
            return out;
        } catch (IOException e) {
            throw new EmbeddingException("invalid ollama json", false, e);
        }
    }

    private static float[] normalize(JsonNode v) {
        float[] f = new float[v.size()];
        double norm = 0;
        for (int i = 0; i < f.length; i++) {
            f[i] = (float) v.get(i).asDouble();
            norm += f[i] * f[i];
        }
        if (norm > 0) {
            float inv = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < f.length; i++) {
                f[i] *= inv;
            }
        }
        return f;
    }
}
