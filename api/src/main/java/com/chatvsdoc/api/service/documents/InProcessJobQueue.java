package com.chatvsdoc.api.service.documents;

import com.chatvsdoc.api.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Hàng đợi trong tiến trình: N worker (mặc định 2, luồng nền tảng vì trích văn bản tốn CPU), tách
 * khỏi luồng xử lý HTTP.
 */
@Component
public class InProcessJobQueue implements JobQueue {

    private static final Logger log = LoggerFactory.getLogger(InProcessJobQueue.class);

    private final DocumentProcessor processor;
    private final ExecutorService pool;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public InProcessJobQueue(@Lazy DocumentProcessor processor, AppProperties props) {
        this.processor = processor;
        int n = props.processing().workers();
        AtomicInteger seq = new AtomicInteger();
        this.pool =
                new ThreadPoolExecutor(
                        n,
                        n,
                        0,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        r -> {
                            Thread t = new Thread(r, "doc-worker-" + seq.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
    }

    @Override
    public void enqueue(UUID documentId) {
        if (!pending.add(documentId)) {
            return;
        }
        pool.execute(
                () -> {
                    // Giữ trong pending tới khi xong: một tài liệu không bao giờ chạy song song.
                    try {
                        processor.processDocument(documentId);
                    } catch (RuntimeException e) {
                        log.error("document_job_failed document_id={}", documentId, e);
                    } finally {
                        pending.remove(documentId);
                    }
                });
    }

    /** Số việc đang chờ (cho test). */
    public int pendingCount() {
        return pending.size();
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
