package com.chatvsdoc.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/** Nhật ký kiểm toán — chỉ thêm (CSDL chặn UPDATE/DELETE, xem V1__init.sql). */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    private UUID actorUserId;

    @Column(nullable = false)
    private String action;

    private String target;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "audit_result", nullable = false)
    private AuditResult result;

    protected AuditLog() {}

    public AuditLog(UUID actorUserId, String action, String target, AuditResult result) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.target = target;
        this.result = result;
    }

    public Long getId() {
        return id;
    }

    public Instant getTs() {
        return ts;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public AuditResult getResult() {
        return result;
    }
}
