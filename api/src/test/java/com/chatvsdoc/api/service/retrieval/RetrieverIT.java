package com.chatvsdoc.api.service.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.support.Corpus;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.pgvector.PGvector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** T12: truy hồi đa tài liệu trên pgvector thật với FakeEmbedding. */
@IntegrationTest
class RetrieverIT {

    @Autowired Retriever retriever;
    @Autowired Fixtures fixtures;
    @Autowired Corpus corpus;
    @Autowired JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void setUp() {
        fixtures.resetDocuments();
        user = fixtures.user();
    }

    @Test
    void onlyReadyNotDeletedDocumentsAreSearched() {
        Document ok = corpus.ready(user, "ok.pdf", "thời hạn thanh toán ba mươi ngày");
        corpus.document(
                user,
                "dang-xu-ly.pdf",
                DocumentStatus.processing,
                "thời hạn thanh toán ba mươi ngày");
        corpus.document(user, "loi.pdf", DocumentStatus.failed, "thời hạn thanh toán ba mươi ngày");
        Document deleted = corpus.ready(user, "da-xoa.pdf", "thời hạn thanh toán ba mươi ngày");
        jdbc.update("UPDATE documents SET deleted_at = now() WHERE id = ?", deleted.getId());

        List<ContextBlock> hits = retriever.search("thời hạn thanh toán");
        assertThat(hits).extracting(ContextBlock::documentId).containsOnly(ok.getId());
    }

    @Test
    void ac021_2_bothDocumentsAppearAndNoneTakesMoreThanFour() {
        String[] many = new String[8];
        Arrays.setAll(many, i -> "thanh toán hóa đơn đợt " + i + " trong ba mươi ngày");
        Document a = corpus.ready(user, "A.pdf", many);
        Document b =
                corpus.ready(
                        user, "B.pdf", "thanh toán hóa đơn trong bốn lăm ngày kể từ nghiệm thu");

        List<ContextBlock> hits = retriever.search("thanh toán hóa đơn trong bao nhiêu ngày");
        assertThat(hits).extracting(ContextBlock::documentId).contains(a.getId(), b.getId());
        assertThat(hits.stream().filter(h -> h.documentId().equals(a.getId())))
                .hasSizeLessThanOrEqualTo(4);
        // n liên tục từ 1, điểm giảm dần; vị trí khớp bản ghi chunk
        for (int i = 0; i < hits.size(); i++) {
            assertThat(hits.get(i).n()).isEqualTo(i + 1);
            if (i > 0) {
                assertThat(hits.get(i).score()).isLessThanOrEqualTo(hits.get(i - 1).score());
            }
            ContextBlock h = hits.get(i);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT page FROM chunks WHERE id = ?",
                                    Integer.class,
                                    h.chunkId()))
                    .isEqualTo(h.page());
            assertThat(h.text().length()).isEqualTo(h.charEnd() - h.charStart());
        }
    }

    @Test
    void outOfScopeQuestionAndEmptyWorkspaceReturnNothing() {
        assertThat(retriever.search("thời hạn thanh toán")).isEmpty();
        corpus.ready(user, "hd.pdf", "thời hạn thanh toán ba mươi ngày");
        // Không có từ nào trùng → cosine 0 < ngưỡng 0,30
        assertThat(retriever.search("Thủ đô của nước Pháp")).isEmpty();
        assertThat(retriever.search("   ")).isEmpty();
    }

    @Test
    void fullWorkspaceRetrievalLatency() {
        // 20 tài liệu × ~300 đoạn ≈ 6.000 đoạn (vector ngẫu nhiên chuẩn hóa) + một đoạn thật.
        Random rnd = new Random(7);
        for (int d = 0; d < 20; d++) {
            Document doc = corpus.ready(user, "big-" + d + ".pdf");
            List<Object[]> rows = new ArrayList<>();
            for (int c = 0; c < 300; c++) {
                float[] v = new float[1024];
                double norm = 0;
                for (int i = 0; i < v.length; i++) {
                    v[i] = (float) rnd.nextGaussian();
                    norm += v[i] * v[i];
                }
                for (int i = 0; i < v.length; i++) {
                    v[i] /= (float) Math.sqrt(norm);
                }
                rows.add(new Object[] {doc.getId(), c % 100 + 1, 0, 5, "noise", new PGvector(v)});
            }
            jdbc.batchUpdate(
                    "INSERT INTO chunks (document_id, page, char_start, char_end, text, embedding) VALUES (?, ?, ?, ?, ?, ?)",
                    rows);
        }
        corpus.ready(user, "that.pdf", "thời hạn thanh toán ba mươi ngày");
        retriever.search("thời hạn thanh toán"); // làm nóng
        long[] ms = new long[20];
        for (int i = 0; i < ms.length; i++) {
            long t0 = System.nanoTime();
            List<ContextBlock> hits = retriever.search("thời hạn thanh toán");
            ms[i] = (System.nanoTime() - t0) / 1_000_000;
            assertThat(hits).isNotEmpty();
        }
        Arrays.sort(ms);
        long p90 = ms[(int) (ms.length * 0.9) - 1];
        LoggerFactory.getLogger(RetrieverIT.class)
                .info("T12 retrieval p90_ms={} chunks~6000 at={}", p90, Instant.now());
        assertThat(p90).isLessThanOrEqualTo(300);
    }

    @Test
    void selectionDeduplicatesOverlapsAndCapsContextLength() {
        UUID doc = UUID.randomUUID();
        List<ContextBlock> candidates =
                List.of(
                        new ContextBlock(
                                0,
                                doc,
                                "a.pdf",
                                1,
                                UUID.randomUUID(),
                                0,
                                100,
                                "x".repeat(100),
                                0.9),
                        new ContextBlock(
                                0,
                                doc,
                                "a.pdf",
                                1,
                                UUID.randomUUID(),
                                40,
                                140,
                                "x".repeat(100),
                                0.88),
                        new ContextBlock(
                                0,
                                doc,
                                "a.pdf",
                                2,
                                UUID.randomUUID(),
                                0,
                                100,
                                "x".repeat(100),
                                0.85),
                        new ContextBlock(
                                0,
                                doc,
                                "a.pdf",
                                3,
                                UUID.randomUUID(),
                                0,
                                100,
                                "x".repeat(100),
                                0.4));
        List<ContextBlock> picked = Retriever.select(candidates, 8, 0.3);
        // đoạn 2 trùng > 50% với đoạn 1 (cùng trang) → bỏ; đoạn 4 < 0,6 × 0,9 → bỏ
        assertThat(picked).extracting(ContextBlock::page).containsExactly(1, 2);
        assertThat(picked).extracting(ContextBlock::n).containsExactly(1, 2);

        List<ContextBlock> big = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            big.add(
                    new ContextBlock(
                            0,
                            UUID.randomUUID(),
                            "d.pdf",
                            1,
                            UUID.randomUUID(),
                            0,
                            5000,
                            "y".repeat(5000),
                            0.9 - i * 0.01));
        }
        List<ContextBlock> capped = Retriever.select(big, 8, 0.3);
        assertThat(capped.stream().mapToInt(c -> c.text().length()).sum())
                .isLessThanOrEqualTo(Retriever.MAX_CONTEXT_CHARS);
        assertThat(Retriever.select(candidates, 8, 0.95)).isEmpty();
    }
}
