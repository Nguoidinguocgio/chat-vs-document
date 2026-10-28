package com.chatvsdoc.api.port.fake;

import com.chatvsdoc.api.port.LlmPort;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * LLM giả (LLM_PROVIDER=fake) cho test và E2E xác định (T13 mục 3.6). Mặc định chọn kịch bản theo
 * từ khóa trong câu hỏi (không phân biệt hoa thường, bỏ dấu); hoặc chạy một {@link Script} cố định.
 *
 * <pre>
 * thanh toan            hai nguồn mâu thuẫn, [1][2]
 * doanh thu             một phần: [1] + dòng &lt;&lt;MISSING&gt;&gt;
 * phap | ngoai pham vi  &lt;&lt;NOT_FOUND&gt;&gt;
 * ngat                  nửa câu trả lời rồi {@link SimulatedConnectionDrop}
 * cham                  không phát token nào trong stall-seconds
 * loi dich vu           LlmException trước token đầu
 * injection             [9], &lt;script&gt;, &lt;img onerror&gt;, liên kết javascript:
 * khác                  câu trả lời ngắn có [1]
 * </pre>
 */
public class FakeLlm implements LlmPort {

    /**
     * Chỉ có ở bản giả: lớp SSE đóng kết nối đột ngột, không gửi done/error (mô phỏng mất mạng).
     */
    public static class SimulatedConnectionDrop extends RuntimeException {
        public SimulatedConnectionDrop() {
            super("simulated connection drop");
        }
    }

    /**
     * Kịch bản cố định: các đoạn chữ, độ trễ giữa đoạn, và lỗi (nếu có) ném sau {@code failAfter}
     * đoạn.
     */
    public record Script(
            List<String> deltas, Duration delay, RuntimeException failure, int failAfter) {

        public Script {
            deltas = List.copyOf(deltas);
        }

        public static Script of(String... deltas) {
            return new Script(List.of(deltas), Duration.ZERO, null, 0);
        }

        public Script withDelay(Duration d) {
            return new Script(deltas, d, failure, failAfter);
        }

        public Script failingAfter(int n, RuntimeException e) {
            return new Script(deltas, delay, e, n);
        }
    }

    static final String CONFLICT =
            "Hai tài liệu quy định thời hạn thanh toán **không thống nhất**.\n\n"
                    + "- Hợp đồng A: thanh toán đợt 1 trong vòng **30 ngày** kể từ ngày nhận hóa đơn hợp lệ [1].\n"
                    + "- Đề xuất B: thời hạn **45 ngày** kể từ ngày nghiệm thu [2].\n\n"
                    + "Đây là mâu thuẫn giữa hai tài liệu nên hệ thống không tự chọn bên nào.";
    static final String PARTIAL =
            "Doanh thu quý 3 đạt **128,4 tỷ đồng**, tăng 12% so với cùng kỳ [1].\n"
                    + "<<MISSING>> Biên lợi nhuận theo từng mảng không có trong các tài liệu hiện có.";
    static final String INJECTION =
            "Theo tài liệu [1][9] <script>alert(1)</script> <img src=x onerror=1> [bấm](javascript:alert(1)).";
    static final String SHORT = "Thông tin liên quan nằm trong tài liệu đã tải lên [1].";

    private volatile Script script;
    private final Duration delay;
    private final Duration stall;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean lastClosed = true;

    /** Chế độ từ khóa với độ trễ 30 ms/mảnh, "cham" im lặng 31 s. */
    public FakeLlm() {
        this(Duration.ofMillis(30), Duration.ofSeconds(31));
    }

    public FakeLlm(Duration delay, Duration stall) {
        this.delay = delay;
        this.stall = stall;
    }

    public FakeLlm(Script script) {
        this(Duration.ZERO, Duration.ofSeconds(31));
        this.script = script;
    }

    /** Đổi sang kịch bản cố định ({@code null}: về chế độ từ khóa). */
    public void setScript(Script script) {
        this.script = script;
    }

    /** Số lần được gọi (test: truy hồi rỗng thì không gọi LLM). */
    public int calls() {
        return calls.get();
    }

    /** Lần gọi gần nhất đã kết thúc/đóng chưa (test: hủy không rò rỉ). */
    public boolean lastClosed() {
        return lastClosed;
    }

    @Override
    public LlmUsage stream(List<LlmMessage> messages, Consumer<String> onDelta) {
        calls.incrementAndGet();
        lastClosed = false;
        try {
            Script s = script != null ? script : forQuestion(question(messages));
            int input = messages.stream().mapToInt(m -> approxTokens(m.content())).sum();
            int output = 0;
            for (int i = 0; i < s.deltas().size(); i++) {
                if (s.failure() != null && i == s.failAfter()) {
                    throw s.failure();
                }
                pause(s.delay());
                String d = s.deltas().get(i);
                onDelta.accept(d);
                output += approxTokens(d);
            }
            if (s.failure() != null && s.failAfter() >= s.deltas().size()) {
                throw s.failure();
            }
            return new LlmUsage(input, output);
        } finally {
            lastClosed = true;
        }
    }

    private Script forQuestion(String q) {
        String k = fold(q);
        if (k.contains("loi dich vu")) {
            return Script.of().failingAfter(0, new LlmException("simulated provider error"));
        }
        if (k.contains("cham")) {
            return new Script(List.of(""), stall, null, 0);
        }
        if (k.contains("phap") || k.contains("ngoai pham vi")) {
            return words("<<NOT_FOUND>>");
        }
        if (k.contains("ngat")) {
            Script full = words(CONFLICT);
            return full.failingAfter(full.deltas().size() / 2, new SimulatedConnectionDrop());
        }
        if (k.contains("thanh toan")) {
            return words(CONFLICT);
        }
        if (k.contains("doanh thu")) {
            return words(PARTIAL);
        }
        if (k.contains("injection")) {
            return words(INJECTION);
        }
        return words(SHORT);
    }

    /** Cắt thành mảnh 1–3 từ như luồng thật (giữ khoảng trắng để ghép lại đúng nguyên văn). */
    private Script words(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int words = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur.append(c);
            if (Character.isWhitespace(c) && ++words % 2 == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        return new Script(parts, delay, null, 0);
    }

    /** Câu hỏi = phần sau "Câu hỏi:" của tin nhắn người dùng cuối (xem Prompts.user). */
    static String question(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if (m.role() == LlmMessage.Role.user) {
                String c = m.content();
                int at = c.lastIndexOf("Câu hỏi:");
                return at >= 0 ? c.substring(at + "Câu hỏi:".length()) : c;
            }
        }
        return "";
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static void pause(Duration d) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("interrupted");
        }
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted");
        }
    }

    private static int approxTokens(String s) {
        return Math.max(1, s.length() / 4);
    }
}
