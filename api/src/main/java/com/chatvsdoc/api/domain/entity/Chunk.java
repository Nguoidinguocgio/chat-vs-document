package com.chatvsdoc.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Đoạn văn bản để truy hồi. Cột {@code embedding} (vector) không ánh xạ qua JPA: ghi/đọc bằng
 * {@link com.chatvsdoc.api.domain.repository.ChunkVectorRepository} (JdbcTemplate + pgvector-java).
 */
@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int page;

    @Column(nullable = false)
    private int charStart;

    @Column(nullable = false)
    private int charEnd;

    @Column(nullable = false)
    private String text;

    protected Chunk() {}

    public Chunk(UUID documentId, int page, int charStart, int charEnd, String text) {
        this.documentId = documentId;
        this.page = page;
        this.charStart = charStart;
        this.charEnd = charEnd;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPage() {
        return page;
    }

    public int getCharStart() {
        return charStart;
    }

    public int getCharEnd() {
        return charEnd;
    }

    public String getText() {
        return text;
    }
}
