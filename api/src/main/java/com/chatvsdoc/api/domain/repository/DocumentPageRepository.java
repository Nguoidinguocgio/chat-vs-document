package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.DocumentPage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, DocumentPage.Key> {
    List<DocumentPage> findByDocumentIdOrderByPage(UUID documentId);
}
