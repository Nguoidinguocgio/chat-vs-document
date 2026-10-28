package com.chatvsdoc.api.service.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Cắt văn bản <b>một trang</b> thành đoạn (T08 bước 8) — không bao giờ cắt qua ranh giới trang. Ưu
 * tiên ranh giới đoạn văn, rồi câu, rồi khoảng trắng; mục tiêu 800 ký tự, tối đa 1.000; chồng lấn
 * 100 ký tự; đoạn ngắn hơn 80 ký tự được ghép vào đoạn kề. Vị trí tính trên văn bản trang đã chuẩn
 * hóa: {@code pageText.substring(start, end).equals(text)}.
 */
public final class Chunker {

    /** Một đoạn: vị trí [start, end) trong văn bản trang. */
    public record Span(int start, int end, String text) {}

    public static final int TARGET = 800;
    public static final int MAX = 1000;
    public static final int OVERLAP = 100;
    public static final int MIN = 80;

    // Đơn vị nhỏ nhất phải chừa chỗ cho phần chồng lấn để đoạn không vượt MAX.
    private static final int MAX_UNIT = MAX - OVERLAP;
    private static final String[] SENTENCE_ENDS = {". ", "? ", "! ", "; "};

    private Chunker() {}

    public static List<Span> chunk(String page) {
        List<int[]> units = new ArrayList<>();
        splitParagraphs(page, units);
        List<int[]> raw = pack(page, units);
        List<Span> out = new ArrayList<>(raw.size());
        for (int[] r : mergeShort(raw)) {
            int[] t = trim(page, r[0], r[1]);
            if (t[1] > t[0]) {
                out.add(new Span(t[0], t[1], page.substring(t[0], t[1])));
            }
        }
        return out;
    }

    private static void splitParagraphs(String s, List<int[]> units) {
        int pos = 0;
        while (pos < s.length()) {
            int brk = s.indexOf("\n\n", pos);
            int end = brk < 0 ? s.length() : brk;
            splitSentences(s, pos, end, units);
            pos = brk < 0 ? s.length() : brk + 2;
        }
    }

    private static void splitSentences(String s, int start, int end, List<int[]> units) {
        if (end - start <= MAX_UNIT) {
            addUnit(s, start, end, units);
            return;
        }
        int pos = start;
        while (pos < end) {
            int cut = -1;
            for (int i = pos; i < end - 1 && i - pos < MAX_UNIT; i++) {
                for (String sep : SENTENCE_ENDS) {
                    if (s.startsWith(sep, i)) {
                        cut = i + sep.length();
                    }
                }
            }
            int stop = end - pos <= MAX_UNIT ? end : (cut > pos ? cut : -1);
            if (stop < 0) {
                stop = splitWords(s, pos, end);
            }
            addUnit(s, pos, stop, units);
            pos = stop;
        }
    }

    // Cắt tại khoảng trắng cuối cùng trong giới hạn; không có khoảng trắng thì cắt cứng.
    private static int splitWords(String s, int pos, int end) {
        int limit = Math.min(end, pos + MAX_UNIT);
        for (int i = limit; i > pos + MAX_UNIT / 2; i--) {
            if (Character.isWhitespace(s.charAt(i - 1))) {
                return i;
            }
        }
        return limit;
    }

    private static void addUnit(String s, int start, int end, List<int[]> units) {
        int[] t = trim(s, start, end);
        if (t[1] > t[0]) {
            units.add(t);
        }
    }

    // Ghép các đơn vị liên tiếp tới ~TARGET; đoạn sau bắt đầu lùi lại OVERLAP ký tự (tại ranh giới
    // từ).
    private static List<int[]> pack(String s, List<int[]> units) {
        List<int[]> chunks = new ArrayList<>();
        int i = 0;
        int chunkStart = -1;
        while (i < units.size()) {
            int start = chunkStart >= 0 ? chunkStart : units.get(i)[0];
            int end = units.get(i)[1];
            i++;
            while (i < units.size() && units.get(i)[1] - start <= TARGET) {
                end = units.get(i)[1];
                i++;
            }
            chunks.add(new int[] {start, end});
            chunkStart = i < units.size() ? overlapStart(s, start, end) : -1;
        }
        return chunks;
    }

    private static int overlapStart(String s, int start, int end) {
        int p = Math.max(start + 1, end - OVERLAP);
        while (p < end && !Character.isWhitespace(s.charAt(p - 1))) {
            p++;
        }
        return p >= end ? end : p;
    }

    private static List<int[]> mergeShort(List<int[]> chunks) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : chunks) {
            if (!out.isEmpty()) {
                int[] prev = out.get(out.size() - 1);
                boolean shortOne = c[1] - c[0] < MIN || prev[1] - prev[0] < MIN;
                if (shortOne && c[1] - prev[0] <= MAX) {
                    prev[1] = Math.max(prev[1], c[1]);
                    continue;
                }
            }
            out.add(new int[] {c[0], c[1]});
        }
        return out;
    }

    private static int[] trim(String s, int start, int end) {
        while (start < end && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }
}
