package com.chatvsdoc.api.web;

import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.chat.ChatStreamService;
import com.chatvsdoc.api.service.chat.ChatViews.ConversationDetail;
import com.chatvsdoc.api.service.chat.ChatViews.ConversationView;
import com.chatvsdoc.api.service.chat.ConversationService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Hội thoại và hỏi đáp qua SSE (T14). */
@RestController
public class ChatController {

    /** Body {@code {question}} — kiểm tra độ dài trong service để trả QUESTION_INVALID. */
    public record AskRequest(String question) {}

    private final ConversationService conversations;
    private final ChatStreamService chat;

    public ChatController(ConversationService conversations, ChatStreamService chat) {
        this.conversations = conversations;
        this.chat = chat;
    }

    @PostMapping("/api/conversations")
    ResponseEntity<ConversationView> create(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(me));
    }

    @GetMapping("/api/conversations")
    List<ConversationView> list(@AuthenticationPrincipal AuthUser me) {
        return conversations.list(me);
    }

    @GetMapping("/api/conversations/{id}")
    ConversationDetail get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return conversations.get(me, id);
    }

    /**
     * Lỗi kiểm tra (422/404/409) trả JSON chuẩn trước khi mở luồng; sau đó {@code
     * text/event-stream}. Không khai báo {@code produces} để phản hồi lỗi JSON vẫn được ghi.
     */
    @PostMapping("/api/conversations/{id}/messages")
    SseEmitter ask(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable UUID id,
            @RequestBody AskRequest req,
            HttpServletResponse response) {
        SseEmitter emitter = chat.ask(me, id, req == null ? null : req.question());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return emitter;
    }
}
