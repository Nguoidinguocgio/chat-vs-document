package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatvsdoc.api.port.EmbeddingPort.EmbeddingException;
import com.chatvsdoc.api.port.adapter.OllamaEmbeddingAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Adapter Ollama với máy chủ HTTP giả (không cần Ollama thật trong CI). */
class OllamaEmbeddingAdapterTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger dims = new AtomicInteger(4);
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/embed",
                ex -> {
                    JsonNode req = json.readTree(ex.getRequestBody());
                    lastRequest.set(req);
                    StringBuilder body = new StringBuilder("{\"embeddings\":[");
                    for (int i = 0; i < req.path("input").size(); i++) {
                        body.append(i == 0 ? "" : ",").append("[");
                        for (int d = 0; d < dims.get(); d++) {
                            body.append(d == 0 ? "" : ",").append(d == i % dims.get() ? "3" : "0");
                        }
                        body.append("]");
                    }
                    body.append("]}");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(status.get(), bytes.length);
                    try (OutputStream out = ex.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OllamaEmbeddingAdapter adapter() {
        return new OllamaEmbeddingAdapter(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                "bge-m3",
                4,
                Duration.ofSeconds(5));
    }

    @Test
    void sendsModelAndInputsAndNormalizes() {
        List<float[]> v = adapter().embedDocuments(List.of("Điều khoản", "Payment terms"));
        assertThat(lastRequest.get().path("model").asText()).isEqualTo("bge-m3");
        assertThat(lastRequest.get().path("input").get(0).asText()).isEqualTo("Điều khoản");
        assertThat(v).hasSize(2);
        assertThat(v.get(0)).containsExactly(1f, 0f, 0f, 0f);
        assertThat(adapter().embedQuery("q")).hasSize(4);
    }

    @Test
    void serverErrorsAreTransientClientErrorsAreNot() {
        status.set(503);
        assertThatThrownBy(() -> adapter().embed(List.of("a")))
                .isInstanceOf(EmbeddingException.class)
                .satisfies(e -> assertThat(((EmbeddingException) e).isTransient()).isTrue());
        status.set(404);
        assertThatThrownBy(() -> adapter().embed(List.of("a")))
                .satisfies(e -> assertThat(((EmbeddingException) e).isTransient()).isFalse());
    }

    @Test
    void wrongDimensionIsRejected() {
        dims.set(3);
        assertThatThrownBy(() -> adapter().embed(List.of("a")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("EMBEDDING_DIM");
    }

    @Test
    void unreachableIsTransient() {
        server.stop(0);
        assertThatThrownBy(() -> adapter().embed(List.of("a")))
                .satisfies(e -> assertThat(((EmbeddingException) e).isTransient()).isTrue());
    }
}
