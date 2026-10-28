package com.chatvsdoc.api.service.auth;

import com.chatvsdoc.api.common.AppException;
import com.chatvsdoc.api.common.ErrorCode;
import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserStatus;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.analytics.AnalyticsService;
import com.chatvsdoc.api.service.audit.AuditService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đăng nhập, đổi mật khẩu (T04 mục 3.2, 3.5). */
@Service
public class AuthService {

    static final int MAX_FAILED = 5;
    static final Duration FAILED_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** Kết quả đăng nhập thành công: người dùng và token để đặt cookie. */
    public record LoginResult(AuthUser user, String token) {}

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final AuditService audit;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    // Băm sẵn một mật khẩu giả để email không tồn tại tốn cùng thời gian (AC-001.2).
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            PasswordHasher hasher,
            SessionService sessions,
            AuditService audit,
            AnalyticsService analytics,
            JdbcTemplate jdbc,
            Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.audit = audit;
        this.analytics = analytics;
        this.jdbc = jdbc;
        this.clock = clock;
        this.dummyHash = hasher.hash("dummy-password-for-timing-1");
    }

    public LoginResult login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            hasher.matches(password, dummyHash);
            throw failed(null, "invalid_credentials", ErrorCode.INVALID_CREDENTIALS);
        }
        User u = found.get();
        Instant now = clock.instant();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            hasher.matches(password, dummyHash);
            long retryAfter = Math.max(1, Duration.between(now, u.getLockedUntil()).toSeconds());
            audit.record(u.getId(), "auth.login", "locked", AuditResult.failure);
            analytics.emit("auth_login_failed", u.getId(), null, Map.of("reason", "locked"));
            throw new AppException(ErrorCode.ACCOUNT_LOCKED, Map.of("retry_after_s", retryAfter));
        }
        if (!hasher.matches(password, u.getPasswordHash())) {
            registerFailure(u.getId(), now);
            throw failed(u.getId(), "invalid_credentials", ErrorCode.INVALID_CREDENTIALS);
        }
        // Chỉ báo bị vô hiệu hóa khi mật khẩu đúng — không lộ trạng thái cho người dò.
        if (u.getStatus() == UserStatus.disabled) {
            throw failed(u.getId(), "disabled", ErrorCode.ACCOUNT_DISABLED);
        }
        return succeed(u, password, now);
    }

    private LoginResult succeed(User u, String password, Instant now) {
        boolean firstLogin = u.getLastLoginAt() == null;
        String newHash = hasher.upgradeEncoding(u.getPasswordHash()) ? hasher.hash(password) : null;
        jdbc.update(
                """
                UPDATE users SET failed_attempts = 0, failed_window_start = NULL, locked_until = NULL,
                  last_login_at = ?, password_hash = COALESCE(?, password_hash)
                WHERE id = ?
                """,
                Timestamp.from(now),
                newHash,
                u.getId());
        SessionService.CreatedSession s = sessions.create(u);
        audit.record(u.getId(), "auth.login", null, AuditResult.success);
        analytics.emit(
                "auth_login_succeeded",
                u.getId(),
                s.sessionId(),
                Map.of("is_first_login", firstLogin));
        return new LoginResult(AuthUser.of(u, s.sessionId()), s.token());
    }

    /**
     * Tăng bộ đếm sai nguyên tử trong SQL (nhiều yêu cầu song song không vượt bộ đếm); đủ {@link
     * #MAX_FAILED} trong cửa sổ thì khóa và đặt lại bộ đếm.
     */
    private void registerFailure(UUID userId, Instant now) {
        Timestamp ts = Timestamp.from(now);
        Timestamp windowStart = Timestamp.from(now.minus(FAILED_WINDOW));
        Integer attempts =
                jdbc.queryForObject(
                        """
                        UPDATE users SET
                          failed_attempts = CASE WHEN failed_window_start IS NULL OR failed_window_start < ?
                                                 THEN 1 ELSE failed_attempts + 1 END,
                          failed_window_start = CASE WHEN failed_window_start IS NULL OR failed_window_start < ?
                                                     THEN ? ELSE failed_window_start END
                        WHERE id = ?
                        RETURNING failed_attempts
                        """,
                        Integer.class,
                        windowStart,
                        windowStart,
                        ts,
                        userId);
        if (attempts != null && attempts >= MAX_FAILED) {
            jdbc.update(
                    "UPDATE users SET locked_until = ?, failed_attempts = 0, failed_window_start = NULL"
                            + " WHERE id = ?",
                    Timestamp.from(now.plus(LOCK_DURATION)),
                    userId);
        }
    }

    private AppException failed(UUID userId, String reason, ErrorCode code) {
        audit.record(userId, "auth.login", reason, AuditResult.failure);
        analytics.emit("auth_login_failed", userId, null, Map.of("reason", reason));
        return new AppException(code);
    }

    /**
     * Đổi mật khẩu. Khi bắt buộc đổi (mật khẩu tạm) không cần mật khẩu hiện tại. Hủy mọi phiên
     * khác.
     */
    @Transactional
    public void changePassword(AuthUser me, String currentPassword, String newPassword) {
        User u =
                users.findById(me.id())
                        .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (!u.isMustChangePassword()
                && (currentPassword == null
                        || !hasher.matches(currentPassword, u.getPasswordHash()))) {
            audit.record(u.getId(), "auth.change_password", null, AuditResult.failure);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!PasswordPolicy.isAcceptable(newPassword)
                || hasher.matches(newPassword, u.getPasswordHash())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR.name(),
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "auth.password_policy",
                    Map.of());
        }
        u.setPasswordHash(hasher.hash(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        sessions.revokeOtherSessions(u.getId(), me.sessionId());
        audit.record(u.getId(), "auth.change_password", null, AuditResult.success);
    }
}
