package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Chunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
    List<Chunk> findByDocumentId(UUID documentId);
}
