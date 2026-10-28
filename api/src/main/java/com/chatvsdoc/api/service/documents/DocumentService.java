package com.chatvsdoc.api.service.documents;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.port.StoragePort;
import com.chatvsdoc.api.service.analytics.AnalyticsService;
import com.chatvsdoc.api.service.audit.AuditService;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.events.EventBus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Tải lên, thử lại, liệt kê tài liệu (T07, T09 mục 3.1). */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Khóa advisory chung của workspace (MVP một workspace) để giữ chỗ nguyên tử. */
    static final long WORKSPACE_LOCK = 0x43_48_41_54_44_4F_43L;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    /** {@code DocumentList} trong hợp đồng. */
    public record DocumentList(List<DocumentView> items, int count, int limit) {}

    private final DocumentRepository docs;
    private final DocumentViews views;
    private final StoragePort storage;
    private final JobQueue queue;
    private final EventBus events;
    private final AuditService audit;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int maxDocs;
    private final long maxBytes;
    private final int orphanMinutes;

    public DocumentService(
            DocumentRepository docs,
            DocumentViews views,
            StoragePort storage,
            JobQueue queue,
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
        this.queue = queue;
        this.events = events;
        this.audit = audit;
        this.analytics = analytics;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.maxDocs = props.maxDocs();
        this.maxBytes = props.maxFileMb() * 1024L * 1024L;
        this.orphanMinutes = props.processing().orphanMinutes();
    }

    /** Tên hiển thị an toàn: bỏ ký tự điều khiển và dấu phân cách đường dẫn, ≤ 255 ký tự. */
    static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "").strip();
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name;
    }

    /**
     * Thứ tự kiểm tra T07 mục 3.1: đuôi → kích thước (dừng đọc ngay) → nội dung {@code %PDF-} → giữ
     * chỗ nguyên tử → lưu file → xếp hàng.
     */
    public DocumentView upload(AuthUser me, String originalName, InputStream in, int batchSize)
            throws IOException {
        String name = sanitizeName(originalName);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw reject(me, ErrorCode.NOT_PDF, "not_pdf");
        }
        Path tmp = Files.createTempFile("upload-", ".pdf");
        try {
            LimitedCopy.Result copied;
            try (OutputStream out = Files.newOutputStream(tmp)) {
                copied = LimitedCopy.copy(in, out, maxBytes);
            } catch (LimitedCopy.TooLargeException e) {
                throw reject(me, ErrorCode.FILE_TOO_LARGE, "too_large");
            }
            if (!Arrays.equals(copied.head(), PDF_MAGIC)) {
                throw reject(me, ErrorCode.NOT_PDF, "not_pdf");
            }
            String key = "documents/" + UUID.randomUUID() + ".pdf";
            Document doc = reserve(me, name, copied, key);
            try (InputStream file = Files.newInputStream(tmp)) {
                storage.put(key, file);
            } catch (IOException | RuntimeException e) {
                docs.deleteById(doc.getId());
                audit.record(
                        me.id(), "document.upload", doc.getId().toString(), AuditResult.failure);
                throw e;
            }
            audit.record(me.id(), "document.upload", doc.getId().toString(), AuditResult.success);
            analytics.emit(
                    "document_upload_started",
                    me.id(),
                    me.sessionId(),
                    Map.of("file_size_bytes", copied.size(), "batch_size", batchSize));
            DocumentView view = views.of(doc);
            events.publish("document.created", view.asEvent());
            queue.enqueue(doc.getId());
            return view.forViewer(me);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // Khóa advisory theo giao dịch: đếm + chèn tuần tự giữa mọi yêu cầu, commit rồi mới nhả khóa.
    private Document reserve(AuthUser me, String name, LimitedCopy.Result copied, String key) {
        Document doc =
                tx.execute(
                        status -> {
                            jdbc.query(
                                    "SELECT pg_advisory_xact_lock(?)", rs -> null, WORKSPACE_LOCK);
                            Integer count =
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM documents WHERE deleted_at IS NULL",
                                            Integer.class);
                            if (count != null && count >= maxDocs) {
                                return null;
                            }
                            return docs.saveAndFlush(
                                    new Document(
                                            name, copied.size(), copied.sha256(), key, me.id()));
                        });
        if (doc == null) {
            AppException e = reject(me, ErrorCode.DOCUMENT_LIMIT_REACHED, "limit_reached");
            throw new AppException(e.code(), e.status(), e.messageKey(), Map.of("limit", maxDocs));
        }
        return doc;
    }

    private AppException reject(AuthUser me, ErrorCode code, String reason) {
        audit.record(me.id(), "document.upload", reason, AuditResult.failure);
        analytics.emit(
                "document_upload_rejected", me.id(), me.sessionId(), Map.of("reason", reason));
        return new AppException(code);
    }

    /** Thử lại khi lỗi hệ thống (AC-012.7). */
    public DocumentView retry(AuthUser me, UUID id) {
        Document updated =
                tx.execute(
                        status -> {
                            Document d =
                                    docs.findById(id)
                                            .filter(x -> x.getDeletedAt() == null)
                                            .orElseThrow(
                                                    () -> new AppException(ErrorCode.NOT_FOUND));
                            if (d.getStatus() != DocumentStatus.failed
                                    || !"SYSTEM_ERROR".equals(d.getErrorCode())) {
                                throw new AppException(ErrorCode.NOT_RETRYABLE);
                            }
                            d.setStatus(DocumentStatus.processing);
                            d.setErrorCode(null);
                            d.setProcessedAt(null);
                            return docs.saveAndFlush(d);
                        });
        audit.record(me.id(), "document.retry", id.toString(), AuditResult.success);
        DocumentView view = views.of(updated);
        events.publish("document.status_changed", view.asEvent());
        queue.enqueue(id);
        return view.forViewer(me);
    }

    /** Mọi tài liệu chưa xóa, mới nhất trước; {@code count} gồm mọi trạng thái. */
    public DocumentList list(AuthUser me) {
        List<Document> active =
                docs.findAll().stream()
                        .filter(d -> d.getDeletedAt() == null)
                        .sorted((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()))
                        .toList();
        List<DocumentView> items = views.list(active).stream().map(v -> v.forViewer(me)).toList();
        return new DocumentList(items, items.size(), maxDocs);
    }

    /** Tài liệu {@code processing} bị bỏ dở do khởi động lại (cũ hơn N phút) → xếp hàng lại. */
    @EventListener(ApplicationReadyEvent.class)
    public void requeueOrphans() {
        List<UUID> orphans =
                jdbc.queryForList(
                        "SELECT id FROM documents WHERE status = 'processing' AND deleted_at IS NULL"
                                + " AND uploaded_at < ?",
                        UUID.class,
                        Timestamp.from(clock.instant().minusSeconds(orphanMinutes * 60L)));
        if (!orphans.isEmpty()) {
            log.info("requeue_orphan_documents count={}", orphans.size());
        }
        orphans.forEach(queue::enqueue);
    }
}
