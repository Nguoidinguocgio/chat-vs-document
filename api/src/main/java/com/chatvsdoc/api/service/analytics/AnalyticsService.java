package com.chatvsdoc.api.service.analytics;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.domain.entity.Event;
import com.chatvsdoc.api.domain.repository.EventRepository;
import com.chatvsdoc.api.service.auth.AuthUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ghi sự kiện analytics vào bảng {@code events} qua cùng một bộ lọc {@link AnalyticsSchema}. */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final Duration MAX_CLIENT_SKEW = Duration.ofHours(24);
    public static final int MAX_BATCH = 50;

    /** Một sự kiện client gửi lên. */
    public record ClientEvent(String name, Instant ts, Map<String, Object> props) {}

    private final EventRepository repo;
    private final Clock clock;
    private final String appVersion;

    public AnalyticsService(EventRepository repo, Clock clock, AppProperties props) {
        this.repo = repo;
        this.clock = clock;
        this.appVersion = props.appVersion();
    }

    /**
     * Sự kiện do máy chủ phát (T04, T07, T08, T14). Sự kiện sai danh sách trắng là lỗi lập trình:
     * bị bỏ và ghi cảnh báo (không làm hỏng nghiệp vụ).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(String name, UUID userId, UUID sessionId, Map<String, Object> props) {
        String violation = AnalyticsSchema.violation(name, props);
        if (violation != null) {
            log.warn("analytics_event_dropped name={} violation={}", name, violation);
            return;
        }
        Map<String, Object> stored = new HashMap<>(props);
        stored.put("app_version", appVersion);
        repo.save(new Event(name, userId, sessionId, stored));
    }

    /** Lô sự kiện từ client: một sự kiện sai thì từ chối cả lô (422). */
    @Transactional
    public void recordClientBatch(AuthUser user, List<ClientEvent> events) {
        if (events == null || events.isEmpty() || events.size() > MAX_BATCH) {
            throw invalid("batch_size");
        }
        Instant now = clock.instant();
        List<Event> rows = new ArrayList<>(events.size());
        for (ClientEvent e : events) {
            if (e.name() == null || !AnalyticsSchema.CLIENT_EVENTS.contains(e.name())) {
                throw invalid("unknown_event");
            }
            Map<String, Object> props = e.props() == null ? Map.of() : e.props();
            String violation = AnalyticsSchema.violation(e.name(), props);
            if (violation != null) {
                throw invalid(violation);
            }
            Map<String, Object> stored = new HashMap<>(props);
            if (e.ts() != null) {
                if (Duration.between(e.ts(), now).abs().compareTo(MAX_CLIENT_SKEW) > 0) {
                    throw invalid("ts_out_of_range");
                }
                stored.put("client_ts", e.ts().toString());
            }
            stored.put("role", user.role().name());
            stored.put("app_version", appVersion);
            rows.add(new Event(e.name(), user.id(), user.sessionId(), stored));
        }
        repo.saveAll(rows);
    }

    private static AppException invalid(String reason) {
        return new AppException(ErrorCode.VALIDATION_ERROR, Map.of("reason", reason));
    }
}
