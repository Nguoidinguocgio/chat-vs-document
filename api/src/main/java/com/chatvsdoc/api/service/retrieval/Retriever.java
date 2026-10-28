package com.chatvsdoc.api.service.retrieval;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.port.EmbeddingPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Truy hồi đa tài liệu (T12 mục 3.2): ngưỡng điểm, chọn đa dạng (≤ 4 đoạn/tài liệu, bỏ đoạn &lt;
 * 0,6 × điểm cao nhất), khử trùng lặp, giới hạn tổng độ dài ngữ cảnh. Không dịch câu hỏi — truy hồi
 * chéo ngôn ngữ dựa vào embedding đa ngôn ngữ.
 */
@Service
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);
    static final int CANDIDATES = 30;
    static final int MAX_PER_DOCUMENT = 4;
    static final double RELATIVE_FLOOR = 0.6;
    static final double OVERLAP_DUPLICATE = 0.5;
    static final int MAX_CONTEXT_CHARS = 24_000;

    private final EmbeddingPort embedding;
    private final ChunkSearchRepository search;
    private final int topK;
    private final double minScore;

    public Retriever(
            @Lazy EmbeddingPort embedding, ChunkSearchRepository search, AppProperties props) {
        this.embedding = embedding;
        this.search = search;
        this.topK = props.retrieval().topK();
        this.minScore = props.retrieval().minScore();
    }

    public List<ContextBlock> search(String question) {
        return search(question, topK, minScore);
    }

    /** Tham số tường minh (CLI {@code search} dùng để chỉnh TOP_K / MIN_SCORE cùng T20). */
    public List<ContextBlock> search(String question, int k, double threshold) {
        long t0 = System.nanoTime();
        String q = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (q.isEmpty()) {
            return List.of();
        }
        List<ContextBlock> candidates = search.nearest(embedding.embedQuery(q), CANDIDATES);
        List<ContextBlock> picked = select(candidates, k, threshold);
        log.info(
                "retrieval",
                kv("candidates", candidates.size()),
                kv("picked", picked.size()),
                kv("top_score", candidates.isEmpty() ? 0 : candidates.get(0).score()),
                kv("retrieval_ms", (System.nanoTime() - t0) / 1_000_000));
        return picked;
    }

    /** Hàm thuần trên danh sách ứng viên đã sắp theo điểm giảm dần (test riêng được). */
    static List<ContextBlock> select(List<ContextBlock> candidates, int k, double threshold) {
        if (candidates.isEmpty() || candidates.get(0).score() < threshold) {
            return List.of();
        }
        double floor = candidates.get(0).score() * RELATIVE_FLOOR;
        Map<UUID, Integer> perDoc = new HashMap<>();
        List<ContextBlock> out = new ArrayList<>();
        for (ContextBlock c : candidates) {
            if (out.size() >= k) {
                break;
            }
            if (c.score() < floor || perDoc.getOrDefault(c.documentId(), 0) >= MAX_PER_DOCUMENT) {
                continue;
            }
            if (out.stream().anyMatch(p -> duplicate(p, c))) {
                continue;
            }
            perDoc.merge(c.documentId(), 1, Integer::sum);
            out.add(c);
        }
        // Cắt bớt từ đoạn điểm thấp nhất nếu tổng ngữ cảnh vượt giới hạn.
        int total = out.stream().mapToInt(b -> b.text().length()).sum();
        while (total > MAX_CONTEXT_CHARS && out.size() > 1) {
            total -= out.remove(out.size() - 1).text().length();
        }
        List<ContextBlock> numbered = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            numbered.add(out.get(i).withN(i + 1));
        }
        return numbered;
    }

    /** Cùng tài liệu, cùng trang, chồng lấn vị trí &gt; 50% đoạn ngắn hơn. */
    private static boolean duplicate(ContextBlock a, ContextBlock b) {
        if (!a.documentId().equals(b.documentId()) || a.page() != b.page()) {
            return false;
        }
        int overlap = Math.min(a.charEnd(), b.charEnd()) - Math.max(a.charStart(), b.charStart());
        int shorter = Math.min(a.charEnd() - a.charStart(), b.charEnd() - b.charStart());
        return shorter > 0 && overlap > OVERLAP_DUPLICATE * shorter;
    }
}
