package com.chatvsdoc.api.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.port.StoragePort;
import com.chatvsdoc.api.support.ApiClient;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.chatvsdoc.api.support.MakeFixtures;
import com.chatvsdoc.api.support.SwitchableEmbedding;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** T08: pipeline PDF với PDF mẫu sinh bằng PDFBox (MakeFixtures). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PdfPipelineIT {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired DocumentRepository docs;
    @Autowired StoragePort storage;
    @Autowired PdfPipeline pipeline;
    @Autowired SwitchableEmbedding embedding;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetDocuments();
        embedding.reset();
        user = fixtures.user();
        api = new ApiClient(port, fixtures.login(mvc, user).getValue());
    }

    @AfterEach
    void tearDown() {
        embedding.reset();
        fixtures.awaitQueueIdle();
    }

    /** Lưu file + bản ghi processing, chạy pipeline đồng bộ (không qua hàng đợi). */
    private Document stored(byte[] pdf) throws Exception {
        String key = "documents/" + UUID.randomUUID() + ".pdf";
        storage.put(key, new ByteArrayInputStream(pdf));
        return docs.save(new Document("mau.pdf", pdf.length, "0".repeat(64), key, user.getId()));
    }

    private Document run(byte[] pdf) throws Exception {
        Document d = stored(pdf);
        pipeline.process(d.getId(), embedding);
        return docs.findById(d.getId()).orElseThrow();
    }

    private int chunkCount(UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class, id);
    }

    @Test
    void ac010_1_vietnamesePdfBecomesReadyWithPageAccurateChunks(CapturedOutput output)
            throws Exception {
        Document d = run(MakeFixtures.okVi5p());
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.ready);
        assertThat(d.getPageCount()).isEqualTo(5);
        assertThat(d.getUnreadablePages()).isEmpty();
        assertThat(d.getProcessedAt()).isNotNull();

        List<Map<String, Object>> chunks =
                jdbc.queryForList(
                        "SELECT c.page, c.char_start, c.char_end, c.text, p.text AS page_text,"
                                + " vector_dims(c.embedding) AS dims"
                                + " FROM chunks c JOIN document_pages p ON p.document_id = c.document_id AND p.page = c.page"
                                + " WHERE c.document_id = ?",
                        d.getId());
        assertThat(chunks).isNotEmpty();
        for (Map<String, Object> c : chunks) {
            int page = (Integer) c.get("page");
            String text = (String) c.get("text");
            String pageText = (String) c.get("page_text");
            // Mỗi chunk chỉ chứa mã của đúng trang nguồn → không cắt qua ranh giới trang.
            assertThat(text).contains("TRANG-" + page);
            for (int other = 1; other <= 5; other++) {
                if (other != page) {
                    assertThat(text).doesNotContain("TRANG-" + other + " ");
                }
            }
            assertThat(
                            pageText.substring(
                                    (Integer) c.get("char_start"), (Integer) c.get("char_end")))
                    .isEqualTo(text);
            assertThat(c.get("dims")).isEqualTo(1024);
        }
        String page1 =
                jdbc.queryForObject(
                        "SELECT text FROM document_pages WHERE document_id = ? AND page = 1",
                        String.class,
                        d.getId());
        assertThat(page1)
                .contains("Điều khoản thanh toán")
                .contains("ba mươi ngày")
                .contains("đồng Việt Nam");
        assertThat(java.text.Normalizer.isNormalized(page1, java.text.Normalizer.Form.NFC))
                .isTrue();
        // Log không chứa văn bản tài liệu.
        assertThat(output.getAll()).doesNotContain("ba mươi ngày").doesNotContain("TRANG-1");
    }

    @Test
    void englishPdfIsReady() throws Exception {
        assertThat(run(MakeFixtures.okEn()).getStatus()).isEqualTo(DocumentStatus.ready);
    }

    @Test
    void ac012_3_tooManyPagesKeepsPageCount() throws Exception {
        Document d = run(MakeFixtures.pages(101));
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.failed);
        assertThat(d.getErrorCode()).isEqualTo("TOO_MANY_PAGES");
        assertThat(d.getPageCount()).isEqualTo(101);
        var list = api.get("/api/documents").json().path("items");
        assertThat(list.get(0).path("error_details").path("page_count").asInt()).isEqualTo(101);
    }

    @Test
    void ac012_4_passwordProtected() throws Exception {
        assertThat(run(MakeFixtures.encrypted()).getErrorCode()).isEqualTo("PASSWORD_PROTECTED");
    }

    @Test
    void ac012_5_scannedImagesOnly() throws Exception {
        Document d = run(MakeFixtures.scannedImagesOnly());
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.failed);
        assertThat(d.getErrorCode()).isEqualTo("SCANNED_PDF");
        assertThat(chunkCount(d.getId())).isZero();
    }

    @Test
    void ac012_6_corrupted() throws Exception {
        assertThat(run(MakeFixtures.corrupted()).getErrorCode()).isEqualTo("CORRUPTED");
    }

    @Test
    void ac012_8_partiallyScannedIsReadyWithUnreadablePages() throws Exception {
        Document d = run(MakeFixtures.mixed3Scan10Text());
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.ready);
        assertThat(d.getPageCount()).isEqualTo(13);
        assertThat(d.getUnreadablePages()).containsExactly(2, 5, 9);
    }

    @Test
    void ac012_7_persistentEmbeddingOutageIsSystemErrorThenRetryRecovers() throws Exception {
        embedding.setFailing(true);
        Document d = run(MakeFixtures.okEn());
        assertThat(d.getErrorCode()).isEqualTo("SYSTEM_ERROR");
        assertThat(embedding.calls()).isEqualTo(4); // 1 lần + 3 lần thử lại
        embedding.setFailing(false);
        assertThat(api.post("/api/documents/" + d.getId() + "/retry").status()).isEqualTo(202);
        awaitStatus(d.getId(), DocumentStatus.ready);
        assertThat(chunkCount(d.getId())).isPositive();
    }

    @Test
    void failureWhileInsertingChunksRollsBackEverything() throws Exception {
        // Lô thứ 2 trả vector sai số chiều → INSERT chunk thất bại giữa chừng.
        embedding.setWrongDimFromBatch(1);
        Document d = run(MakeFixtures.pages(40));
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.failed);
        assertThat(d.getErrorCode()).isEqualTo("SYSTEM_ERROR");
        assertThat(chunkCount(d.getId())).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM document_pages WHERE document_id = ?",
                                Integer.class,
                                d.getId()))
                .isZero();
    }

    @Test
    void reprocessingIsIdempotent() throws Exception {
        Document d = run(MakeFixtures.okVi5p());
        int first = chunkCount(d.getId());
        jdbc.update("UPDATE documents SET status = 'processing' WHERE id = ?", d.getId());
        pipeline.process(d.getId(), embedding);
        assertThat(chunkCount(d.getId())).isEqualTo(first);
        assertThat(docs.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.ready);
    }

    @Test
    void endToEndUploadThenReadyAndAnalytics() throws Exception {
        ApiClient.Response r = api.upload("Hợp đồng.pdf", MakeFixtures.okVi5p());
        UUID id = UUID.fromString(r.json().path("id").asText());
        awaitStatus(id, DocumentStatus.ready);
        // Sự kiện được ghi ngay sau khi commit trạng thái → chờ một chút.
        List<String> events = List.of();
        for (int i = 0; i < 100 && events.isEmpty(); i++) {
            Fixtures.sleep(50);
            events =
                    jdbc.queryForList(
                            "SELECT name || ' ' || props::text FROM events WHERE props->>'document_id' = ?",
                            String.class,
                            id.toString());
        }
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("document_processing_completed")
                                        && e.contains("\"page_count\": 5")
                                        && e.contains("unreadable_page_count"));

        Document failed = run(MakeFixtures.scannedImagesOnly());
        assertThat(
                        jdbc.queryForList(
                                "SELECT props->>'reason' FROM events WHERE name = 'document_processing_failed' AND props->>'document_id' = ?",
                                String.class,
                                failed.getId().toString()))
                .containsExactly("scanned_pdf");
    }

    @Test
    void hundredPagesWithRealisticEmbeddingLatencyUnderSixtySeconds() throws Exception {
        embedding.setDelayMs(150); // giả lập ~150 ms mỗi lô 32 đoạn
        Instant t0 = Instant.now();
        Document d = run(MakeFixtures.pages(100));
        Duration took = Duration.between(t0, Instant.now());
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.ready);
        LoggerFactory.getLogger(PdfPipelineIT.class)
                .info(
                        "TH-03 100 pages processed_ms={} chunks={}",
                        took.toMillis(),
                        chunkCount(d.getId()));
        assertThat(took).isLessThan(Duration.ofSeconds(60));
    }

    private void awaitStatus(UUID id, DocumentStatus expected) {
        Instant until = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(until)
                && docs.findById(id).orElseThrow().getStatus() != expected) {
            Fixtures.sleep(50);
        }
        assertThat(docs.findById(id).orElseThrow().getStatus()).isEqualTo(expected);
    }
}
