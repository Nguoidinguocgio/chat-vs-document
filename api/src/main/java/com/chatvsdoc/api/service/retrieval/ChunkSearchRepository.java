package com.chatvsdoc.api.service.retrieval;

import com.pgvector.PGvector;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tìm đoạn gần nhất bằng pgvector (cosine, chỉ mục HNSW) — chỉ tài liệu sẵn sàng, chưa xóa. */
@Repository
public class ChunkSearchRepository {

    private final JdbcTemplate jdbc;

    public ChunkSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContextBlock> nearest(float[] query, int limit) {
        PGvector q = new PGvector(query);
        return jdbc.query(
                """
                SELECT c.id, c.document_id, d.name, c.page, c.char_start, c.char_end, c.text,
                       1 - (c.embedding <=> ?) AS score
                FROM chunks c JOIN documents d ON d.id = c.document_id
                WHERE d.status = 'ready' AND d.deleted_at IS NULL AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?
                LIMIT ?
                """,
                (rs, i) ->
                        new ContextBlock(
                                0,
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getInt(4),
                                rs.getObject(1, UUID.class),
                                rs.getInt(5),
                                rs.getInt(6),
                                rs.getString(7),
                                rs.getDouble(8)),
                q,
                q,
                limit);
    }
}
