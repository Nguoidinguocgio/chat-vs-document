package com.chatvsdoc.api.service.pdf;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa văn bản một trang (T08 bước 5): Unicode NFC, bỏ ký tự điều khiển, gộp khoảng trắng và
 * ngắt dòng trong đoạn thành một dấu cách, giữ ngắt đoạn (dòng trống) thành đúng {@code \n\n}.
 */
public final class TextNormalizer {

    private static final Pattern NEWLINES = Pattern.compile("\\r\\n?");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t\\u00A0]*\\n\\s*");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]|[\\u200B\\uFEFF]");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0]+");

    private TextNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC);
        s = NEWLINES.matcher(s).replaceAll("\n");
        s = CONTROL.matcher(s).replaceAll(" ");
        String[] paragraphs = PARAGRAPH_BREAK.split(s);
        StringBuilder out = new StringBuilder(s.length());
        for (String p : paragraphs) {
            String collapsed = SPACES.matcher(p).replaceAll(" ").strip();
            if (collapsed.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append(collapsed);
        }
        return out.toString();
    }

    /** Số ký tự "thật" (không tính khoảng trắng) để quyết định trang có đọc được không. */
    public static int meaningfulLength(String normalized) {
        int n = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (!Character.isWhitespace(normalized.charAt(i))) {
                n++;
            }
        }
        return n;
    }
}
