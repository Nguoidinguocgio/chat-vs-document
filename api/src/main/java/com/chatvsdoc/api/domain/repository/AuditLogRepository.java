package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
