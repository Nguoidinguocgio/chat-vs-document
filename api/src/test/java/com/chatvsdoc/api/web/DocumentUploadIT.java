package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.port.StoragePort;
import com.chatvsdoc.api.service.documents.DocumentService;
import com.chatvsdoc.api.support.ApiClient;
import com.chatvsdoc.api.support.ApiClient.Response;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.chatvsdoc.api.support.MakeFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** T07: tải lên, giới hạn 20 tài liệu (kể cả song song), thử lại, mồ côi. */
@IntegrationTest
class DocumentUploadIT {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired DocumentRepository docs;
    @Autowired DocumentService documentService;
    @Autowired StoragePort storage;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private ApiClient api;
    private byte[] pdf;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetDocuments();
        user = fixtures.user();
        api = new ApiClient(port, fixtures.login(mvc, user).getValue());
        pdf = MakeFixtures.okEn();
    }

    private void insertDocs(int n, DocumentStatus status) {
        for (int i = 0; i < n; i++) {
            Document d =
                    new Document(
                            "co-san-" + i + ".pdf",
                            10,
                            "0".repeat(64),
                            "documents/x-" + UUID.randomUUID() + ".pdf",
                            user.getId());
            d.setStatus(status);
            docs.save(d);
        }
    }

    private long activeCount() {
        return docs.findAll().stream().filter(d -> d.getDeletedAt() == null).count();
    }

    @Test
    void ac010_1_validPdfIsStoredAndProcessing() throws Exception {
        Response r = api.upload("Hợp đồng A (bản cuối).pdf", pdf);
        assertThat(r.status()).isEqualTo(201);
        JsonNode body = r.json();
        assertThat(body.path("status").asText()).isEqualTo("processing");
        assertThat(body.path("page_count").isNull()).isTrue();
        assertThat(body.path("name").asText()).isEqualTo("Hợp đồng A (bản cuối).pdf");
        assertThat(body.path("uploaded_by").path("display_name").asText())
                .isEqualTo("Người dùng thử");
        assertThat(body.has("uploaded_by") && !body.path("uploaded_by").has("email")).isTrue();
        assertThat(body.path("can_delete").asBoolean()).isTrue();

        Document d = docs.findById(UUID.fromString(body.path("id").asText())).orElseThrow();
        assertThat(d.getStorageKey()).matches("documents/[0-9a-f-]{36}\\.pdf");
        assertThat(d.getSizeBytes()).isEqualTo(pdf.length);
        assertThat(d.getSha256()).hasSize(64);
        assertThat(storage.size(d.getStorageKey())).isEqualTo(pdf.length);
    }

    @Test
    void namesWithCharactersIllegalInWindowsPathsAreAccepted() throws Exception {
        // Trước đây FileItemInput.getName() gọi Paths.get → 500 trên máy chủ Windows.
        ApiClient.Response r = api.upload("Báo cáo: Q3? <bản> *cuối*|.pdf", pdf);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().path("name").asText()).isEqualTo("Báo cáo: Q3? <bản> *cuối*|.pdf");
    }

    @Test
    void pathTraversalNameIsSanitizedAndNeverUsedAsPath() throws Exception {
        Response r = api.upload("../../x.pdf", pdf);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().path("name").asText()).isEqualTo("x.pdf");
        Document d = docs.findById(UUID.fromString(r.json().path("id").asText())).orElseThrow();
        assertThat(d.getStorageKey()).doesNotContain("x.pdf").doesNotContain("..");
    }

    @Test
    void ac012_1_contentIsCheckedNotJustExtension() throws Exception {
        byte[] docx = "PK\u0003\u0004 word/document.xml".getBytes(StandardCharsets.ISO_8859_1);
        Response renamed = api.upload("bao-cao.pdf", docx);
        assertThat(renamed.status()).isEqualTo(400);
        assertThat(renamed.json().path("error").path("code").asText()).isEqualTo("NOT_PDF");
        Response txt =
                api.upload(
                        "ghi-chu.txt", "%PDF-1.7 nhưng đuôi txt".getBytes(StandardCharsets.UTF_8));
        assertThat(txt.status()).isEqualTo(400);
        assertThat(activeCount()).isZero();
    }

    @Test
    void ac012_2_tenMegabytesPlusOneIs413AndNothingStored() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(pdf, 0, big, 0, pdf.length);
        Response r = api.upload("lon.pdf", big);
        assertThat(r.status()).isEqualTo(413);
        assertThat(r.json().path("error").path("code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(activeCount()).isZero();

        byte[] exact = new byte[10 * 1024 * 1024];
        System.arraycopy(pdf, 0, exact, 0, pdf.length);
        assertThat(api.upload("vua-du.pdf", exact).status()).isEqualTo(201);
    }

    @Test
    void ac010_4_limitCountsEveryStatusAndStoresNothing() throws Exception {
        insertDocs(18, DocumentStatus.ready);
        insertDocs(1, DocumentStatus.processing);
        insertDocs(1, DocumentStatus.failed);
        Response r = api.upload("thu-21.pdf", pdf);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.json().path("error").path("code").asText())
                .isEqualTo("DOCUMENT_LIMIT_REACHED");
        assertThat(r.json().path("error").path("message_key").asText())
                .isEqualTo("documents.limit_reached");
        assertThat(r.json().path("error").path("details").path("limit").asInt()).isEqualTo(20);
        assertThat(activeCount()).isEqualTo(20);
    }

    @Test
    void ac010_5_eighteenPlusFourSequentialGivesTwoAndTwo() throws Exception {
        insertDocs(18, DocumentStatus.ready);
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            statuses.add(api.upload("lo-" + i + ".pdf", pdf).status());
        }
        assertThat(statuses).containsExactly(201, 201, 409, 409);
    }

    @Test
    void raceFiveParallelUploadsForLastSlotExactlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            for (int round = 0; round < 20; round++) {
                fixtures.resetDocuments();
                insertDocs(19, DocumentStatus.ready);
                CountDownLatch go = new CountDownLatch(1);
                List<Callable<Integer>> calls = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    int n = i;
                    calls.add(
                            () -> {
                                go.await();
                                return api.upload("song-song-" + n + ".pdf", pdf).status();
                            });
                }
                List<Future<Integer>> futures = new ArrayList<>();
                calls.forEach(c -> futures.add(pool.submit(c)));
                go.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> f : futures) {
                    statuses.add(f.get());
                }
                assertThat(statuses).as("round " + round).containsOnly(201, 409);
                assertThat(statuses.stream().filter(s -> s == 201).count())
                        .as("round " + round)
                        .isEqualTo(1);
                assertThat(activeCount()).as("round " + round).isEqualTo(20);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void ac012_7_retryOnlyForSystemError() throws Exception {
        Response created = api.upload("thu-lai.pdf", pdf);
        UUID id = UUID.fromString(created.json().path("id").asText());
        fixtures.awaitQueueIdle();
        jdbc.update(
                "UPDATE documents SET status = 'failed', error_code = 'CORRUPTED' WHERE id = ?",
                id);
        Response notRetryable = api.post("/api/documents/" + id + "/retry");
        assertThat(notRetryable.status()).isEqualTo(409);
        assertThat(notRetryable.json().path("error").path("code").asText())
                .isEqualTo("NOT_RETRYABLE");

        jdbc.update(
                "UPDATE documents SET status = 'failed', error_code = 'SYSTEM_ERROR' WHERE id = ?",
                id);
        Response retried = api.post("/api/documents/" + id + "/retry");
        assertThat(retried.status()).isEqualTo(202);
        assertThat(retried.json().path("status").asText()).isEqualTo("processing");
        assertThat(retried.json().path("error_code").isNull()).isTrue();
        awaitStatus(id, DocumentStatus.ready);

        assertThat(api.post("/api/documents/" + UUID.randomUUID() + "/retry").status())
                .isEqualTo(404);
    }

    @Test
    void uploadsAndRejectionsAreAuditedAndTrackedWithoutFileName() throws Exception {
        api.upload("Bí-mật-tên-file.pdf", pdf);
        api.upload("Bí-mật-khác.txt", pdf);
        List<String> events =
                jdbc.queryForList(
                        "SELECT name || ' ' || props::text FROM events WHERE user_id = ? ORDER BY id",
                        String.class,
                        user.getId());
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("document_upload_started")
                                        && e.contains("\"file_size_bytes\": " + pdf.length)
                                        && e.contains("\"batch_size\": 1"));
        assertThat(events)
                .anyMatch(e -> e.startsWith("document_upload_rejected") && e.contains("not_pdf"));
        assertThat(String.join(" ", events)).doesNotContain("Bí-mật");
        List<String> audit =
                jdbc.queryForList(
                        "SELECT result::text FROM audit_logs WHERE action = 'document.upload' AND actor_user_id = ? ORDER BY id",
                        String.class,
                        user.getId());
        assertThat(audit).containsExactly("success", "failure");
    }

    @Test
    void orphanedProcessingDocumentsAreRequeuedOnStartup() throws Exception {
        String key = "documents/" + UUID.randomUUID() + ".pdf";
        try (InputStream in = new ByteArrayInputStream(pdf)) {
            storage.put(key, in);
        }
        Document d =
                docs.save(
                        new Document("mo-coi.pdf", pdf.length, "0".repeat(64), key, user.getId()));
        jdbc.update(
                "UPDATE documents SET uploaded_at = now() - interval '10 minutes' WHERE id = ?",
                d.getId());
        Document fresh =
                docs.save(new Document("moi.pdf", pdf.length, "0".repeat(64), key, user.getId()));
        documentService.requeueOrphans();
        awaitStatus(d.getId(), DocumentStatus.ready);
        // Tài liệu processing mới (< 5 phút) không bị xếp lại (nó có thể đang được xử lý).
        assertThat(docs.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.processing);
    }

    @Test
    void requiresLoginAndCsrfHeader() throws Exception {
        assertThat(new ApiClient(port, null).upload("a.pdf", pdf).status()).isEqualTo(401);
        assertThat(api.upload("a.pdf", pdf, false).status()).isEqualTo(403);
        assertThat(activeCount()).isZero();
    }

    private void awaitStatus(UUID id, DocumentStatus expected) {
        Instant until = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(until)) {
            if (docs.findById(id).orElseThrow().getStatus() == expected) {
                return;
            }
            Fixtures.sleep(50);
        }
        assertThat(docs.findById(id).orElseThrow().getStatus()).isEqualTo(expected);
    }

    static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }
}
