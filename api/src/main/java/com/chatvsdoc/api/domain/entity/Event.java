package com.chatvsdoc.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Sự kiện analytics; {@code props} không chứa nội dung câu hỏi/câu trả lời/tài liệu. */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    @Column(nullable = false)
    private String name;

    private UUID userId;
    private UUID sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> props = Map.of();

    protected Event() {}

    public Event(String name, UUID userId, UUID sessionId, Map<String, Object> props) {
        this.name = name;
        this.userId = userId;
        this.sessionId = sessionId;
        this.props = Map.copyOf(props);
    }

    public Long getId() {
        return id;
    }

    public Instant getTs() {
        return ts;
    }

    public String getName() {
        return name;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getProps() {
        return props;
    }
}
