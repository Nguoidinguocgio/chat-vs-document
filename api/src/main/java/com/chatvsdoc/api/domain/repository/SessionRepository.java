package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByTokenHash(String tokenHash);

    long deleteByUserId(UUID userId);
}
