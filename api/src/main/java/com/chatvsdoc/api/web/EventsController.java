package com.chatvsdoc.api.web;

import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.events.EventService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Kênh sự kiện thời gian thực {@code GET /api/events} (T09 mục 3.2). */
@RestController
public class EventsController {

    private final EventService events;

    public EventsController(EventService events) {
        this.events = events;
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @AuthenticationPrincipal AuthUser me,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        return events.open(me, parseId(lastEventId));
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // ID lạ → coi như quá cũ (−1 không bao giờ nằm trong vòng đệm) để client resync.
            return -1L;
        }
    }
}
