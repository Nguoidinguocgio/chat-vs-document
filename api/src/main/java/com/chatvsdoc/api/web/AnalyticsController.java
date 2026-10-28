package com.chatvsdoc.api.web;

import com.chatvsdoc.api.service.analytics.AnalyticsService;
import com.chatvsdoc.api.service.analytics.AnalyticsService.ClientEvent;
import com.chatvsdoc.api.service.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Nhận lô sự kiện analytics từ client (T09 mục 3.3). */
@RestController
public class AnalyticsController {

    public record Batch(
            @NotNull @Size(min = 1, max = AnalyticsService.MAX_BATCH) List<ClientEvent> events) {}

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @PostMapping("/api/analytics/events")
    ResponseEntity<Void> record(
            @AuthenticationPrincipal AuthUser me, @Valid @RequestBody Batch batch) {
        analytics.recordClientBatch(me, batch.events());
        return ResponseEntity.noContent().build();
    }
}
