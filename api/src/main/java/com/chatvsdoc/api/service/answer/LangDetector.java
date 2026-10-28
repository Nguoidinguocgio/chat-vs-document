package com.chatvsdoc.api.service.answer;

import java.text.Normalizer;

/**
 * Ngôn ngữ câu hỏi (T13 mục 3.2): {@code vi} nếu có ít nhất một chữ cái tiếng Việt có dấu, ngược
 * lại {@code en}. Heuristic đủ cho MVP (câu hỏi tiếng Việt không dấu hiếm).
 */
public final class LangDetector {

    private static final String VI =
            "ăâđêôơưàáạảãấầẩẫậắằẳẵặèéẹẻẽếềểễệìíịỉĩòóọỏõốồổỗộớờởỡợùúụủũứừửữựỳýỵỷỹ";

    private LangDetector() {}

    public static String detect(String question) {
        if (question == null) {
            return "en";
        }
        String s =
                Normalizer.normalize(question, Normalizer.Form.NFC)
                        .toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < s.length(); i++) {
            if (VI.indexOf(s.charAt(i)) >= 0) {
                return "vi";
            }
        }
        return "en";
    }
}
