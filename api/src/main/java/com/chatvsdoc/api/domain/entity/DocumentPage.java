package com.chatvsdoc.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "document_pages")
@IdClass(DocumentPage.Key.class)
public class DocumentPage {

    /** Khóa chính (document_id, page). */
    public record Key(UUID documentId, int page) implements Serializable {}

    @Id private UUID documentId;

    @Id private int page;

    @Column(nullable = false)
    private String text;

    protected DocumentPage() {}

    public DocumentPage(UUID documentId, int page, String text) {
        this.documentId = documentId;
        this.page = page;
        this.text = text;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPage() {
        return page;
    }

    public String getText() {
        return text;
    }
}
