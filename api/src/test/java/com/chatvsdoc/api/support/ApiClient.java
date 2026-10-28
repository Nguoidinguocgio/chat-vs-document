package com.chatvsdoc.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Client HTTP thật (cổng ngẫu nhiên) cho upload multipart và SSE, mang cookie {@code sid}. */
public class ApiClient {

    public static final ObjectMapper JSON = new ObjectMapper();

    /** Phản hồi đã đọc hết. */
    public record Response(int status, String body) {
        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new IllegalStateException(body, e);
            }
        }
    }

    /** Một sự kiện SSE đã phân tích. */
    public record SseEvent(String id, String name, String data) {}

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private final String sid;

    public ApiClient(int port, String sid) {
        this.base = "http://localhost:" + port;
        this.sid = sid;
    }

    private HttpRequest.Builder req(String path) {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
        if (sid != null) {
            b.header("Cookie", "sid=" + sid);
        }
        return b;
    }

    public Response get(String path) throws Exception {
        HttpResponse<String> r =
                http.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(), r.body());
    }

    public Response post(String path) throws Exception {
        HttpResponse<String> r =
                http.send(
                        req(path)
                                .header("X-Requested-With", "fetch")
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(), r.body());
    }

    /** Upload {@code multipart/form-data} trường {@code file}. */
    public Response upload(String filename, byte[] content) throws Exception {
        return upload(filename, content, true);
    }

    public Response upload(String filename, byte[] content, boolean csrfHeader) throws Exception {
        String boundary = "----test" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(
                ("--"
                                + boundary
                                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                                + filename
                                + "\"\r\nContent-Type: application/pdf\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b =
                req("/api/documents")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (csrfHeader) {
            b.header("X-Requested-With", "fetch");
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        } catch (IOException e) {
            // Máy chủ có thể đóng kết nối khi từ chối sớm body quá lớn.
            return new Response(-1, e.toString());
        }
    }

    /** POST JSON. */
    public Response postJson(String path, String json) throws Exception {
        HttpResponse<String> r =
                http.send(
                        req(path)
                                .header("X-Requested-With", "fetch")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(), r.body());
    }

    /**
     * POST rồi đọc phản hồi dạng luồng SSE (hỏi đáp). Lỗi trước khi mở luồng: đọc qua {@code
     * body()}.
     */
    public SseStream postSse(String path, String json) throws Exception {
        HttpRequest r =
                req(path)
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Requested-With", "fetch")
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream, application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
        return new SseStream(http.send(r, HttpResponse.BodyHandlers.ofInputStream()));
    }

    /** Kết nối SSE {@code /api/events}; sự kiện được đẩy vào hàng đợi khi đến. */
    public SseStream events(String lastEventId) throws Exception {
        HttpRequest.Builder b =
                req("/api/events")
                        // Chỉ giới hạn thời gian chờ header; luồng sau đó mở tùy ý.
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "text/event-stream");
        if (lastEventId != null) {
            b.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> r =
                http.send(b.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        return new SseStream(r);
    }

    /** Luồng SSE đang mở. */
    public static final class SseStream implements AutoCloseable {
        public final int status;
        private final InputStream in;
        private final BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
        private final List<String> comments = new ArrayList<>();
        private volatile boolean ended;

        SseStream(HttpResponse<InputStream> r) {
            this.status = r.statusCode();
            this.in = r.body();
            Thread.ofVirtual().start(this::read);
        }

        private void read() {
            try (BufferedReader br =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String id = null;
                String name = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (name != null || !data.isEmpty()) {
                            events.add(
                                    new SseEvent(
                                            id, name == null ? "message" : name, data.toString()));
                        }
                        id = null;
                        name = null;
                        data.setLength(0);
                    } else if (line.startsWith(":")) {
                        synchronized (comments) {
                            comments.add(line);
                        }
                    } else if (line.startsWith("id:")) {
                        id = line.substring(3).trim();
                    } else if (line.startsWith("event:")) {
                        name = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    }
                }
            } catch (IOException e) {
                // kết nối bị đóng
            } finally {
                ended = true;
            }
        }

        /** Sự kiện kế tiếp có tên {@code name} (bỏ qua heartbeat nếu không hỏi heartbeat). */
        public SseEvent next(String name, long timeoutMs) throws InterruptedException {
            long until = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < until) {
                SseEvent e = events.poll(50, TimeUnit.MILLISECONDS);
                if (e != null && e.name().equals(name)) {
                    return e;
                }
            }
            return null;
        }

        /** Mọi sự kiện không phải heartbeat nhận trong khoảng chờ. */
        public List<SseEvent> drain(long waitMs) throws InterruptedException {
            List<SseEvent> out = new ArrayList<>();
            long until = System.currentTimeMillis() + waitMs;
            while (System.currentTimeMillis() < until) {
                SseEvent e = events.poll(50, TimeUnit.MILLISECONDS);
                if (e != null && !"heartbeat".equals(e.name())) {
                    out.add(e);
                }
            }
            return out;
        }

        public boolean awaitEnd(long timeoutMs) {
            long until = System.currentTimeMillis() + timeoutMs;
            while (!ended && System.currentTimeMillis() < until) {
                Fixtures.sleep(20);
            }
            return ended;
        }

        public int commentCount() {
            synchronized (comments) {
                return comments.size();
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
