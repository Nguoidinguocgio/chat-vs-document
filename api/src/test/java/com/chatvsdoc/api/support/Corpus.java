package com.chatvsdoc.api.support;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.port.fake.FakeEmbedding;
import com.pgvector.PGvector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** Dựng nhanh tài liệu + đoạn có vector (FakeEmbedding) cho test truy hồi / hỏi đáp. */
@TestComponent
public class Corpus {

    private final DocumentRepository docs;
    private final JdbcTemplate jdbc;
    private final FakeEmbedding embedding = new FakeEmbedding(1024);

    public Corpus(DocumentRepository docs, JdbcTemplate jdbc) {
        this.docs = docs;
        this.jdbc = jdbc;
    }

    /** Một đoạn = một trang (trang 1, 2, …). */
    public Document document(User owner, String name, DocumentStatus status, String... chunks) {
        Document d = new Document(name, 1, "0".repeat(64), "k/" + UUID.randomUUID(), owner.getId());
        d.setStatus(status);
        d.setPageCount(chunks.length);
        docs.saveAndFlush(d);
        List<float[]> vectors = embedding.embed(List.of(chunks));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < chunks.length; i++) {
            rows.add(
                    new Object[] {
                        d.getId(),
                        i + 1,
                        0,
                        chunks[i].length(),
                        chunks[i],
                        new PGvector(vectors.get(i))
                    });
        }
        jdbc.batchUpdate(
                "INSERT INTO chunks (document_id, page, char_start, char_end, text, embedding) VALUES (?, ?, ?, ?, ?, ?)",
                rows);
        return d;
    }

    public Document ready(User owner, String name, String... chunks) {
        return document(owner, name, DocumentStatus.ready, chunks);
    }
}
