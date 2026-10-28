package com.chatvsdoc.api.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static String paragraph(int words, String seed) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            sb.append(seed).append(i).append(i % 12 == 11 ? ". " : " ");
        }
        return sb.toString().strip();
    }

    private static void assertInvariants(String page, List<Chunker.Span> spans) {
        assertThat(spans).isNotEmpty();
        for (Chunker.Span s : spans) {
            assertThat(page.substring(s.start(), s.end())).isEqualTo(s.text());
            assertThat(s.end() - s.start()).isLessThanOrEqualTo(Chunker.MAX);
            assertThat(s.text()).isEqualTo(s.text().strip());
        }
        // Phủ toàn bộ văn bản: đầu đoạn đầu ở đầu trang, cuối đoạn cuối ở cuối trang.
        assertThat(spans.get(0).start()).isZero();
        assertThat(spans.get(spans.size() - 1).end()).isEqualTo(page.length());
    }

    @Test
    void shortPageIsOneChunk() {
        String page = "Điều khoản thanh toán trong ba mươi ngày.";
        List<Chunker.Span> spans = Chunker.chunk(page);
        assertThat(spans).singleElement().extracting(Chunker.Span::text).isEqualTo(page);
    }

    @Test
    void longPageSplitsNearTargetWithOverlap() {
        String page = paragraph(400, "từ") + "\n\n" + paragraph(300, "chữ");
        List<Chunker.Span> spans = Chunker.chunk(page);
        assertInvariants(page, spans);
        assertThat(spans.size()).isGreaterThan(3);
        for (int i = 1; i < spans.size(); i++) {
            Chunker.Span prev = spans.get(i - 1);
            Chunker.Span cur = spans.get(i);
            assertThat(cur.start()).isLessThanOrEqualTo(prev.end());
            assertThat(prev.end() - cur.start()).isLessThanOrEqualTo(Chunker.OVERLAP);
        }
        // Đa số đoạn gần mục tiêu 800 (trừ đoạn cuối mỗi phần).
        long nearTarget = spans.stream().filter(s -> s.text().length() > 500).count();
        assertThat(nearTarget).isGreaterThanOrEqualTo(spans.size() - 2);
    }

    @Test
    void prefersParagraphBoundaries() {
        String a = paragraph(60, "a");
        String b = paragraph(60, "b");
        String page = a + "\n\n" + b;
        List<Chunker.Span> spans = Chunker.chunk(page);
        assertInvariants(page, spans);
    }

    @Test
    void wordWithoutSpacesIsHardCutWithinMax() {
        String page = "x".repeat(2500);
        List<Chunker.Span> spans = Chunker.chunk(page);
        assertInvariants(page, spans);
        assertThat(spans.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void shortTailIsMergedIntoNeighbour() {
        String page = paragraph(130, "w") + "\n\nCuối.";
        List<Chunker.Span> spans = Chunker.chunk(page);
        assertInvariants(page, spans);
        assertThat(spans).noneMatch(s -> s.text().equals("Cuối."));
    }

    @Test
    void vietnameseDiacriticsAreKeptIntact() {
        String page = TextNormalizer.normalize("Hợp đồng mua bán — điều khoản bảo hành 12 tháng.");
        assertThat(Chunker.chunk(page).get(0).text()).contains("Hợp đồng").contains("bảo hành");
    }
}
