package com.chatvsdoc.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** Trích dẫn thứ {@code idx} của một câu trả lời. */
@Entity
@Table(name = "citations")
@IdClass(Citation.Key.class)
public class Citation {

    /** Khóa chính (message_id, idx). */
    public record Key(UUID messageId, int idx) implements Serializable {}

    @Id private UUID messageId;

    @Id private int idx;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int page;

    private UUID chunkId;

    protected Citation() {}

    public Citation(UUID messageId, int idx, UUID documentId, int page, UUID chunkId) {
        this.messageId = messageId;
        this.idx = idx;
        this.documentId = documentId;
        this.page = page;
        this.chunkId = chunkId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public int getIdx() {
        return idx;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPage() {
        return page;
    }

    public UUID getChunkId() {
        return chunkId;
    }
}
