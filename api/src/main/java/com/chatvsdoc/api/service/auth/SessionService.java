package com.chatvsdoc.api.service.auth;

import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.config.AppProperties;
import com.chatvsdoc.api.domain.entity.Session;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserStatus;
import com.chatvsdoc.api.domain.repository.SessionRepository;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.events.EventBus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo, xác thực và thu hồi phiên cookie {@code sid} (T04 mục 3.3). */
@Service
public class SessionService {

    /** Giới hạn ghi {@code last_seen_at}: tối đa 1 lần/phút mỗi phiên. */
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    /** Kết quả xác thực cookie: người dùng, hoặc mã lỗi cần trả nếu yêu cầu cần đăng nhập. */
    public record Resolution(AuthUser user, ErrorCode error) {
        static Resolution ok(AuthUser u) {
            return new Resolution(u, null);
        }

        static Resolution fail(ErrorCode e) {
            return new Resolution(null, e);
        }
    }

    private final SessionRepository sessions;
    private final UserRepository users;
    private final TokenGenerator tokens;
    private final JdbcTemplate jdbc;
    private final EventBus events;
    private final Clock clock;
    private final Duration idle;

    public SessionService(
            SessionRepository sessions,
            UserRepository users,
            TokenGenerator tokens,
            JdbcTemplate jdbc,
            EventBus events,
            Clock clock,
            AppProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
        this.idle = Duration.ofHours(props.sessionIdleHours());
    }

    /** Phiên mới cho người dùng; trả token gốc (chỉ đặt vào cookie, không lưu). */
    @Transactional
    public CreatedSession create(User user) {
        String token = tokens.newToken();
        Session s = new Session(TokenGenerator.sha256Hex(token), user.getId());
        s.setLastSeenAt(clock.instant());
        sessions.save(s);
        return new CreatedSession(token, s.getId());
    }

    /** Token gốc và ID phiên vừa tạo. */
    public record CreatedSession(String token, UUID sessionId) {}

    /**
     * Xác thực token cookie. Phiên rảnh quá {@code SESSION_IDLE_HOURS} hoặc người dùng đã bị vô
     * hiệu hóa → xóa phiên, {@code SESSION_EXPIRED}.
     */
    @Transactional
    public Resolution resolve(String token) {
        Optional<Session> found = sessions.findByTokenHash(TokenGenerator.sha256Hex(token));
        if (found.isEmpty()) {
            return Resolution.fail(ErrorCode.UNAUTHENTICATED);
        }
        Session s = found.get();
        Instant now = clock.instant();
        if (Duration.between(s.getLastSeenAt(), now).compareTo(idle) > 0) {
            sessions.delete(s);
            return Resolution.fail(ErrorCode.SESSION_EXPIRED);
        }
        Optional<User> user = users.findById(s.getUserId());
        if (user.isEmpty() || user.get().getStatus() == UserStatus.disabled) {
            sessions.delete(s);
            return Resolution.fail(ErrorCode.SESSION_EXPIRED);
        }
        if (Duration.between(s.getLastSeenAt(), now).compareTo(TOUCH_INTERVAL) > 0) {
            jdbc.update(
                    "UPDATE sessions SET last_seen_at = ? WHERE id = ?",
                    Timestamp.from(now),
                    s.getId());
        }
        return Resolution.ok(AuthUser.of(user.get(), s.getId()));
    }

    @Transactional
    public void delete(UUID sessionId) {
        sessions.deleteById(sessionId);
    }

    /** Hủy mọi phiên của người dùng (vô hiệu hóa) và báo kênh SSE của họ đóng lại. */
    @Transactional
    public void revokeUserSessions(UUID userId) {
        sessions.deleteByUserId(userId);
        events.publishTo(userId, "session.revoked", Map.of());
    }

    /** Hủy mọi phiên khác của người dùng, giữ phiên hiện tại (đổi mật khẩu). */
    @Transactional
    public void revokeOtherSessions(UUID userId, UUID keepSessionId) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ? AND id <> ?", userId, keepSessionId);
    }
}
