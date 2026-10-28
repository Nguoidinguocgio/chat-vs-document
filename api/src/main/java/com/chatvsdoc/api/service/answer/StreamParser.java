package com.chatvsdoc.api.service.answer;

import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.service.retrieval.ContextBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Máy trạng thái trên văn bản LLM đang đến (T13 mục 3.4). Chịu được mảnh bị cắt ở bất kỳ vị trí nào
 * ({@code ["[", "1", "]"]}, {@code ["<<NOT", "_FOUND>>"]}).
 *
 * <ul>
 *   <li>{@code <<NOT_FOUND>>} ở đầu → không phát văn bản, kết thúc {@code not_found}.
 *   <li>{@code [n]} hợp lệ (1..số nguồn) → giữ trong văn bản, phát {@link AnswerEvent.Citation} lần
 *       đầu; số ngoài phạm vi hoặc không phải số → bỏ khỏi văn bản (chống trích dẫn bịa).
 *   <li>Dòng {@code <<MISSING>> …} → không phát ra client, kết thúc {@code partial} kèm nội dung.
 * </ul>
 */
public final class StreamParser {

    /** Kết quả khi luồng kết thúc. */
    public record Outcome(AnswerType answerType, String missing, boolean uncited) {}

    private enum Phase {
        START,
        BODY,
        MISSING,
        NOT_FOUND
    }

    static final int START_BUFFER = 24;
    static final int MAX_CITATION_INNER = 6;
    private static final Pattern NUMBER = Pattern.compile("\\d{1,4}");

    private final List<ContextBlock> blocks;
    private final Consumer<AnswerEvent> sink;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder missing = new StringBuilder();
    private final Set<Integer> cited = new HashSet<>();
    private Phase phase = Phase.START;
    private boolean anyText;

    public StreamParser(List<ContextBlock> blocks, Consumer<AnswerEvent> sink) {
        this.blocks = blocks;
        this.sink = sink;
    }

    public void feed(String chunk) {
        if (chunk != null && !chunk.isEmpty()) {
            pending.append(chunk);
            process(false);
        }
    }

    public Outcome finish() {
        process(true);
        if (phase == Phase.NOT_FOUND) {
            return new Outcome(AnswerType.not_found, null, false);
        }
        if (phase == Phase.MISSING) {
            String m = missing.toString().strip();
            return new Outcome(
                    anyText ? AnswerType.partial : AnswerType.not_found,
                    m.isEmpty() ? null : m,
                    anyText && cited.isEmpty());
        }
        if (!anyText) {
            return new Outcome(AnswerType.not_found, null, false);
        }
        return new Outcome(AnswerType.answered, null, cited.isEmpty());
    }

    public int citationCount() {
        return cited.size();
    }

    private void process(boolean end) {
        if (phase == Phase.START) {
            String t = pending.toString().stripLeading();
            if (t.startsWith(Prompts.NOT_FOUND)) {
                phase = Phase.NOT_FOUND;
            } else if (!end && pending.length() < START_BUFFER && Prompts.NOT_FOUND.startsWith(t)) {
                return; // có thể là tiền tố của <<NOT_FOUND>>: chờ thêm
            } else {
                phase = Phase.BODY;
            }
        }
        if (phase == Phase.NOT_FOUND) {
            pending.setLength(0);
            return;
        }
        if (phase == Phase.MISSING) {
            missing.append(pending);
            pending.setLength(0);
            return;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pending.length()) {
            char c = pending.charAt(i);
            if (c == '[') {
                int close = pending.indexOf("]", i);
                if (close < 0) {
                    if (!end && pending.length() - i <= MAX_CITATION_INNER + 1) {
                        break; // chờ dấu đóng
                    }
                    out.append(c); // không phải trích dẫn: giữ nguyên văn
                    i++;
                    continue;
                }
                String inner = pending.substring(i + 1, close);
                if (inner.length() > MAX_CITATION_INNER) {
                    out.append(c);
                    i++;
                    continue;
                }
                if (NUMBER.matcher(inner).matches()) {
                    int n = Integer.parseInt(inner);
                    if (n >= 1 && n <= blocks.size()) {
                        out.append('[').append(n).append(']');
                        emit(out);
                        if (cited.add(n)) {
                            sink.accept(new AnswerEvent.Citation(n, blocks.get(n - 1)));
                        }
                    }
                }
                // số ngoài phạm vi / không phải số: bỏ cả cặp ngoặc khỏi văn bản
                i = close + 1;
                continue;
            }
            if (c == '<') {
                String rest = pending.substring(i);
                if (rest.startsWith(Prompts.MISSING)) {
                    emit(out);
                    phase = Phase.MISSING;
                    missing.append(rest.substring(Prompts.MISSING.length()));
                    pending.setLength(0);
                    return;
                }
                if (rest.startsWith(Prompts.NOT_FOUND)) {
                    i += Prompts.NOT_FOUND.length(); // dấu hiệu lạc giữa văn bản: bỏ
                    continue;
                }
                if (!end
                        && (Prompts.MISSING.startsWith(rest)
                                || Prompts.NOT_FOUND.startsWith(rest))) {
                    break; // có thể là tiền tố dấu hiệu: chờ thêm
                }
            }
            out.append(c);
            i++;
        }
        emit(out);
        pending.delete(0, i);
    }

    private void emit(StringBuilder out) {
        if (out.isEmpty()) {
            return;
        }
        String s = out.toString();
        out.setLength(0);
        if (!s.isBlank()) {
            anyText = true;
        }
        sink.accept(new AnswerEvent.Delta(s));
    }
}
