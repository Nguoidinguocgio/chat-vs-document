package com.chatvsdoc.api.domain.repository;

import com.pgvector.PGvector;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập cột {@code chunks.embedding} (pgvector) bằng JdbcTemplate; JPA không ánh xạ cột này. */
@Repository
public class ChunkVectorRepository {

    /** Đoạn gần nhất cùng độ tương đồng cosine (1 = trùng hướng). */
    public record ScoredChunk(UUID chunkId, double score) {}

    private final JdbcTemplate jdbc;

    public ChunkVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void updateEmbedding(UUID chunkId, float[] embedding) {
        jdbc.update(
                "UPDATE chunks SET embedding = ? WHERE id = ?", new PGvector(embedding), chunkId);
    }

    public Optional<float[]> findEmbedding(UUID chunkId) {
        List<float[]> rows =
                jdbc.query(
                        "SELECT embedding FROM chunks WHERE id = ? AND embedding IS NOT NULL",
                        (rs, i) -> toArray(rs.getObject(1)),
                        chunkId);
        return rows.stream().findFirst();
    }

    /** Tìm {@code k} đoạn gần nhất trong các tài liệu chưa bị xóa (chỉ mục HNSW, cosine). */
    public List<ScoredChunk> nearest(float[] query, int k) {
        PGvector q = new PGvector(query);
        return jdbc.query(
                """
                SELECT c.id, 1 - (c.embedding <=> ?) AS score
                FROM chunks c JOIN documents d ON d.id = c.document_id
                WHERE d.deleted_at IS NULL AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?
                LIMIT ?
                """,
                (rs, i) -> new ScoredChunk(rs.getObject(1, UUID.class), rs.getDouble(2)),
                q,
                q,
                k);
    }

    private static float[] toArray(Object value) throws SQLException {
        return new PGvector(value.toString()).toArray();
    }
}
