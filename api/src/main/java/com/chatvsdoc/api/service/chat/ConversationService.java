package com.chatvsdoc.api.service.chat;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.domain.entity.Conversation;
import com.chatvsdoc.api.domain.entity.MessageStatus;
import com.chatvsdoc.api.domain.repository.ConversationRepository;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.chat.ChatViews.CitationView;
import com.chatvsdoc.api.service.chat.ChatViews.ConversationDetail;
import com.chatvsdoc.api.service.chat.ChatViews.ConversationView;
import com.chatvsdoc.api.service.chat.ChatViews.MessageView;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Hội thoại của người gọi (T14 mục 3.1). Hội thoại của người khác hoặc không tồn tại đều {@code
 * 404} (không phân biệt; kể cả Admin không đọc được hội thoại người khác).
 */
@Service
public class ConversationService {

    public static final String NEW_TITLE = "Hội thoại mới";
    static final int TITLE_MAX = 40;
    static final int LIST_LIMIT = 50;

    private final ConversationRepository conversations;
    private final JdbcTemplate jdbc;

    public ConversationService(ConversationRepository conversations, JdbcTemplate jdbc) {
        this.conversations = conversations;
        this.jdbc = jdbc;
    }

    static ConversationView view(Conversation c) {
        return new ConversationView(
                c.getId(), c.getTitle(), c.getCreatedAt(), c.getLastActivityAt());
    }

    public ConversationView create(AuthUser me) {
        return view(conversations.saveAndFlush(new Conversation(me.id(), NEW_TITLE)));
    }

    public List<ConversationView> list(AuthUser me) {
        return jdbc.query(
                "SELECT id, title, created_at, last_activity_at FROM conversations WHERE user_id = ?"
                        + " ORDER BY last_activity_at DESC LIMIT ?",
                (rs, i) ->
                        new ConversationView(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getTimestamp(3).toInstant(),
                                rs.getTimestamp(4).toInstant()),
                me.id(),
                LIST_LIMIT);
    }

    /** Hội thoại của chính người gọi, hoặc {@code 404}. */
    public Conversation requireOwned(AuthUser me, UUID id) {
        return conversations
                .findById(id)
                .filter(c -> c.getUserId().equals(me.id()))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    public ConversationDetail get(AuthUser me, UUID id) {
        Conversation c = requireOwned(me, id);
        Map<UUID, List<CitationView>> cites = new HashMap<>();
        jdbc.query(
                """
                SELECT ci.message_id, ci.idx, ci.document_id, d.name, ci.page, d.deleted_at IS NOT NULL
                FROM citations ci JOIN messages m ON m.id = ci.message_id
                JOIN documents d ON d.id = ci.document_id
                WHERE m.conversation_id = ? ORDER BY ci.message_id, ci.idx
                """,
                rs -> {
                    cites.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(
                                    new CitationView(
                                            rs.getInt(2),
                                            rs.getObject(3, UUID.class),
                                            rs.getString(4),
                                            rs.getInt(5),
                                            rs.getBoolean(6)));
                },
                id);
        List<MessageView> messages =
                jdbc.query(
                        """
                        SELECT id, question, answer, lang, status::text, answer_type::text, missing,
                               error_code, created_at
                        FROM messages WHERE conversation_id = ? ORDER BY created_at, id
                        """,
                        (rs, i) -> {
                            UUID mid = rs.getObject(1, UUID.class);
                            String type = rs.getString(6);
                            return new MessageView(
                                    mid,
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    MessageStatus.valueOf(rs.getString(5)),
                                    type == null ? null : AnswerType.valueOf(type),
                                    rs.getString(7),
                                    rs.getString(8),
                                    cites.getOrDefault(mid, List.of()),
                                    rs.getTimestamp(9).toInstant());
                        },
                        id);
        return new ConversationDetail(view(c), messages);
    }

    /** 40 ký tự đầu của câu hỏi, cắt ở ranh giới từ, thêm "…". */
    static String titleFrom(String question) {
        String q = question.strip().replaceAll("\\s+", " ");
        if (q.length() <= TITLE_MAX) {
            return q;
        }
        String cut = q.substring(0, TITLE_MAX);
        int space = cut.lastIndexOf(' ');
        if (space > TITLE_MAX / 2) {
            cut = cut.substring(0, space);
        }
        return cut.stripTrailing() + "…";
    }

    /** Đặt tiêu đề từ câu hỏi đầu tiên (chỉ khi còn tiêu đề mặc định) và cập nhật hoạt động. */
    public void touch(UUID conversationId, String firstQuestion, Timestamp now) {
        jdbc.update(
                "UPDATE conversations SET last_activity_at = ?,"
                        + " title = CASE WHEN title = ? THEN ? ELSE title END WHERE id = ?",
                now,
                NEW_TITLE,
                titleFrom(firstQuestion),
                conversationId);
    }
}
