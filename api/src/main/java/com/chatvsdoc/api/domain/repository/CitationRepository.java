package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Citation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitationRepository extends JpaRepository<Citation, Citation.Key> {
    List<Citation> findByMessageIdOrderByIdx(UUID messageId);
}
