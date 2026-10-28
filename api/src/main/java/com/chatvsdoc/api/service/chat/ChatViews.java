package com.chatvsdoc.api.service.chat;

import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.domain.entity.MessageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTO hội thoại theo hợp đồng API (T06: Conversation, Message, Citation, ConversationDetail). */
public final class ChatViews {

    private ChatViews() {}

    public record ConversationView(
            UUID id, String title, Instant createdAt, Instant lastActivityAt) {}

    public record CitationView(
            int index, UUID documentId, String documentName, int page, boolean documentDeleted) {}

    public record MessageView(
            UUID id,
            String question,
            String answer,
            String lang,
            MessageStatus status,
            AnswerType answerType,
            String missing,
            String errorCode,
            List<CitationView> citations,
            Instant createdAt) {}

    public record ConversationDetail(ConversationView conversation, List<MessageView> messages) {}
}
