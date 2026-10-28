package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Document;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {}
