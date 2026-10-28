package com.chatvsdoc.api.service.pdf;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.port.EmbeddingPort;
import com.chatvsdoc.api.port.EmbeddingPort.EmbeddingException;
import com.chatvsdoc.api.port.StoragePort;
import com.chatvsdoc.api.service.analytics.AnalyticsService;
import com.chatvsdoc.api.service.audit.AuditService;
import com.chatvsdoc.api.service.documents.DocumentProcessor;
import com.chatvsdoc.api.service.documents.DocumentViews;
import com.chatvsdoc.api.service.events.EventBus;
import com.pgvector.PGvector;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Biến PDF đã tải lên thành văn bản từng trang + đoạn có vector (T08). Không OCR (D-008). Không bao
 * giờ ghi văn bản tài liệu vào log.
 */
@Service
public class PdfPipeline implements DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PdfPipeline.class);
    static final int MAX_EMBED_RETRIES = 3;

    /** Mã lỗi xử lý ({@code documents.error_code}). */
    public enum ErrorCode {
        TOO_MANY_PAGES,
        PASSWORD_PROTECTED,
        SCANNED_PDF,
        CORRUPTED,
        SYSTEM_ERROR
    }

    /** Thất bại có phân loại; {@code pageCount} có khi đã biết số trang. */
    static final class Failure extends Exception {
        final ErrorCode code;
        final Integer pageCount;

        Failure(ErrorCode code, Integer pageCount) {
            super(code.name());
            this.code = code;
            this.pageCount = pageCount;
        }
    }

    record PageText(int page, String text, boolean readable) {}

    record PendingChunk(int page, Chunker.Span span) {}

    private final DocumentRepository docs;
    private final DocumentViews views;
    private final StoragePort storage;
    private final EmbeddingPort embedding;
    private final EventBus events;
    private final AuditService audit;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AppProperties.Processing cfg;
    private final int maxPages;

    public PdfPipeline(
            DocumentRepository docs,
            DocumentViews views,
            StoragePort storage,
            @Lazy EmbeddingPort embedding,
            EventBus events,
            AuditService audit,
            AnalyticsService analytics,
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            Clock clock,
            AppProperties props) {
        this.docs = docs;
        this.views = views;
        this.storage = storage;
        this.embedding = embedding;
        this.events = events;
        this.audit = audit;
        this.analytics = analytics;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.cfg = props.processing();
        this.maxPages = props.maxPages();
    }

    @Override
    public void processDocument(UUID documentId) {
        process(documentId, embedding);
    }

    /** Chạy pipeline với một {@link EmbeddingPort} cụ thể (test tiêm lỗi qua tham số này). */
    public void process(UUID documentId, EmbeddingPort embedder) {
        Document doc = docs.findById(documentId).orElse(null);
        if (doc == null
                || doc.getDeletedAt() != null
                || doc.getStatus() != DocumentStatus.processing) {
            return;
        }
        long start = System.nanoTime();
        long deadline = start + cfg.timeoutSeconds() * 1_000_000_000L;
        try {
            List<PageText> pages = extract(doc, deadline);
            long extractMs = (System.nanoTime() - start) / 1_000_000;
            List<PendingChunk> chunks = new ArrayList<>();
            for (PageText p : pages) {
                if (p.readable()) {
                    Chunker.chunk(p.text()).forEach(s -> chunks.add(new PendingChunk(p.page(), s)));
                }
            }
            List<float[]> vectors = embedAll(embedder, chunks, deadline);
            long embedMs = (System.nanoTime() - start) / 1_000_000 - extractMs;
            List<Integer> unreadable =
                    pages.stream().filter(p -> !p.readable()).map(PageText::page).toList();
            write(doc.getId(), pages, chunks, vectors, unreadable);
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "document_processed",
                    kv("document_id", doc.getId()),
                    kv("page_count", pages.size()),
                    kv("chunk_count", chunks.size()),
                    kv("extract_ms", extractMs),
                    kv("embed_ms", embedMs),
                    kv("total_ms", totalMs));
            finished(doc.getId());
            analytics.emit(
                    "document_processing_completed",
                    doc.getUploadedBy(),
                    null,
                    Map.of(
                            "document_id", doc.getId().toString(),
                            "page_count", pages.size(),
                            "processing_ms", totalMs,
                            "unreadable_page_count", unreadable.size()));
            audit.record(null, "document.process", doc.getId().toString(), AuditResult.success);
        } catch (Failure f) {
            fail(doc, f.code, f.pageCount);
        } catch (RuntimeException e) {
            // Chỉ ghi loại lỗi + stack trace; không ghi văn bản tài liệu.
            log.error("document_processing_error document_id={}", doc.getId(), e);
            fail(doc, ErrorCode.SYSTEM_ERROR, null);
        }
    }

    private List<PageText> extract(Document doc, long deadline) throws Failure {
        byte[] bytes;
        try (InputStream in = storage.open(doc.getStorageKey())) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read stored file", e);
        }
        PDDocument pdf;
        try {
            pdf = Loader.loadPDF(bytes);
        } catch (InvalidPasswordException e) {
            throw new Failure(ErrorCode.PASSWORD_PROTECTED, null);
        } catch (IOException e) {
            throw new Failure(ErrorCode.CORRUPTED, null);
        }
        try (pdf) {
            int n = pdf.getNumberOfPages();
            if (n == 0) {
                throw new Failure(ErrorCode.CORRUPTED, null);
            }
            if (n > maxPages) {
                throw new Failure(ErrorCode.TOO_MANY_PAGES, n);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n");
            List<PageText> pages = new ArrayList<>(n);
            for (int p = 1; p <= n; p++) {
                checkDeadline(deadline);
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = TextNormalizer.normalize(stripper.getText(pdf));
                boolean readable = TextNormalizer.meaningfulLength(text) >= cfg.minPageChars();
                pages.add(new PageText(p, readable ? text : "", readable));
            }
            if (pages.stream().noneMatch(PageText::readable)) {
                throw new Failure(ErrorCode.SCANNED_PDF, n);
            }
            return pages;
        } catch (IOException e) {
            throw new Failure(ErrorCode.CORRUPTED, null);
        }
    }

    private List<float[]> embedAll(EmbeddingPort embedder, List<PendingChunk> chunks, long deadline)
            throws Failure {
        List<float[]> out = new ArrayList<>(chunks.size());
        int batch = cfg.embeddingBatchSize();
        for (int i = 0; i < chunks.size(); i += batch) {
            checkDeadline(deadline);
            List<String> texts =
                    chunks.subList(i, Math.min(chunks.size(), i + batch)).stream()
                            .map(c -> c.span().text())
                            .toList();
            out.addAll(embedWithRetry(embedder, texts));
        }
        return out;
    }

    // Thử lại tối đa 3 lần khi lỗi tạm thời, chờ 1 s, 2 s, 4 s (nhân với retry-backoff-ms / 1000).
    private List<float[]> embedWithRetry(EmbeddingPort embedder, List<String> texts)
            throws Failure {
        for (int attempt = 0; ; attempt++) {
            try {
                return embedder.embedDocuments(texts);
            } catch (EmbeddingException e) {
                if (!e.isTransient() || attempt >= MAX_EMBED_RETRIES) {
                    log.warn(
                            "embedding_failed attempts={} transient={}",
                            attempt + 1,
                            e.isTransient());
                    throw new Failure(ErrorCode.SYSTEM_ERROR, null);
                }
                sleep(cfg.retryBackoffMs() << attempt);
            }
        }
    }

    private void write(
            UUID id,
            List<PageText> pages,
            List<PendingChunk> chunks,
            List<float[]> vectors,
            List<Integer> unreadable) {
        tx.executeWithoutResult(
                status -> {
                    // Chạy lại cho cùng tài liệu là idempotent: xóa dữ liệu cũ trong cùng giao
                    // dịch.
                    jdbc.update("DELETE FROM chunks WHERE document_id = ?", id);
                    jdbc.update("DELETE FROM document_pages WHERE document_id = ?", id);
                    jdbc.batchUpdate(
                            "INSERT INTO document_pages (document_id, page, text) VALUES (?, ?, ?)",
                            pages.stream()
                                    .map(p -> new Object[] {id, p.page(), p.text()})
                                    .toList());
                    List<Object[]> rows = new ArrayList<>(chunks.size());
                    for (int i = 0; i < chunks.size(); i++) {
                        PendingChunk c = chunks.get(i);
                        rows.add(
                                new Object[] {
                                    id,
                                    c.page(),
                                    c.span().start(),
                                    c.span().end(),
                                    c.span().text(),
                                    new PGvector(vectors.get(i))
                                });
                    }
                    jdbc.batchUpdate(
                            "INSERT INTO chunks (document_id, page, char_start, char_end, text, embedding)"
                                    + " VALUES (?, ?, ?, ?, ?, ?)",
                            rows);
                    jdbc.update(
                            "UPDATE documents SET page_count = ?, unreadable_pages = ?::int[],"
                                    + " status = 'ready', error_code = NULL, processed_at = ?"
                                    + " WHERE id = ?",
                            pages.size(),
                            unreadable.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(",", "{", "}")),
                            Timestamp.from(clock.instant()),
                            id);
                });
    }

    private void fail(Document doc, ErrorCode code, Integer pageCount) {
        jdbc.update(
                "UPDATE documents SET status = 'failed', error_code = ?,"
                        + " page_count = COALESCE(?, page_count), processed_at = ? WHERE id = ?",
                code.name(),
                pageCount,
                Timestamp.from(clock.instant()),
                doc.getId());
        finished(doc.getId());
        analytics.emit(
                "document_processing_failed",
                doc.getUploadedBy(),
                null,
                Map.of(
                        "document_id",
                        doc.getId().toString(),
                        "reason",
                        code.name().toLowerCase(Locale.ROOT)));
        audit.record(null, "document.process", doc.getId().toString(), AuditResult.failure);
    }

    private void finished(UUID id) {
        docs.findById(id)
                .ifPresent(d -> events.publish("document.status_changed", views.of(d).asEvent()));
    }

    private static void checkDeadline(long deadline) throws Failure {
        if (System.nanoTime() > deadline) {
            throw new Failure(ErrorCode.SYSTEM_ERROR, null);
        }
    }

    private static void sleep(long ms) throws Failure {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure(ErrorCode.SYSTEM_ERROR, null);
        }
    }
}
