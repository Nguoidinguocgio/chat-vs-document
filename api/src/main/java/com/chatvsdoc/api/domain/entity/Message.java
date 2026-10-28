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
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/** Một lượt hỏi–đáp trong hội thoại. */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer = "";

    @Column(nullable = false)
    private String lang;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "answer_type")
    private AnswerType answerType;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "message_status", nullable = false)
    private MessageStatus status = MessageStatus.streaming;

    private String missing;
    private String errorCode;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;
    private Integer ttftMs;
    private Integer totalMs;
    private Integer llmInputTokens;
    private Integer llmOutputTokens;

    protected Message() {}

    public Message(UUID conversationId, String question, String lang) {
        this.conversationId = conversationId;
        this.question = question;
        this.lang = lang;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getLang() {
        return lang;
    }

    public AnswerType getAnswerType() {
        return answerType;
    }

    public void setAnswerType(AnswerType answerType) {
        this.answerType = answerType;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public String getMissing() {
        return missing;
    }

    public void setMissing(String missing) {
        this.missing = missing;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getTtftMs() {
        return ttftMs;
    }

    public void setTtftMs(Integer ttftMs) {
        this.ttftMs = ttftMs;
    }

    public Integer getTotalMs() {
        return totalMs;
    }

    public void setTotalMs(Integer totalMs) {
        this.totalMs = totalMs;
    }

    public Integer getLlmInputTokens() {
        return llmInputTokens;
    }

    public void setLlmInputTokens(Integer llmInputTokens) {
        this.llmInputTokens = llmInputTokens;
    }

    public Integer getLlmOutputTokens() {
        return llmOutputTokens;
    }

    public void setLlmOutputTokens(Integer llmOutputTokens) {
        this.llmOutputTokens = llmOutputTokens;
    }
}
