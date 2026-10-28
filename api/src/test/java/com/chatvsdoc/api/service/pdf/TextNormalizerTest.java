package com.chatvsdoc.api.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void collapsesWhitespaceButKeepsParagraphBreaks() {
        assertThat(
                        TextNormalizer.normalize(
                                "Dòng một\ncòn tiếp   theo\r\n\r\n\n  Đoạn hai\t\tkết."))
                .isEqualTo("Dòng một còn tiếp theo\n\nĐoạn hai kết.");
    }

    @Test
    void convertsToNfc() {
        String nfd = Normalizer.normalize("Điều khoản thanh toán", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("Điều khoản thanh toán");
        assertThat(TextNormalizer.normalize(nfd)).isEqualTo("Điều khoản thanh toán");
    }

    @Test
    void removesControlCharacters() {
        assertThat(TextNormalizer.normalize("a\u0000b\u0007c​d")).isEqualTo("a b c d");
    }

    @Test
    void meaningfulLengthIgnoresWhitespace() {
        assertThat(TextNormalizer.meaningfulLength("a b\n\nc")).isEqualTo(3);
        assertThat(TextNormalizer.normalize("   \n\n  ")).isEmpty();
    }
}
