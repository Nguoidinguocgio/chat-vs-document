package com.chatvsdoc.api.service.auth;

import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.entity.UserStatus;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.audit.AuditService;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo/vô hiệu hóa người dùng — dùng từ CLI (T03 mục 3.6). */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final SessionService sessions;
    private final PasswordHasher hasher;
    private final AuditService audit;

    public UserAdminService(
            UserRepository users,
            SessionService sessions,
            PasswordHasher hasher,
            AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.audit = audit;
    }

    @Transactional
    public User createUser(
            String email,
            String displayName,
            UserRole role,
            String password,
            boolean temporaryPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("email không hợp lệ");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("--name là bắt buộc");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("--password là bắt buộc");
        }
        if (users.findByEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("email đã tồn tại");
        }
        User u = new User(normalized, displayName.trim(), hasher.hash(password), role);
        u.setMustChangePassword(temporaryPassword);
        users.save(u);
        audit.record(null, "user.create", u.getId().toString(), AuditResult.success);
        return u;
    }

    /** Vô hiệu hóa và thu hồi mọi phiên đang mở của người dùng. */
    @Transactional
    public User disableUser(String email) {
        User u =
                users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new IllegalArgumentException("không tìm thấy email"));
        u.setStatus(UserStatus.disabled);
        sessions.revokeUserSessions(u.getId());
        audit.record(null, "user.disable", u.getId().toString(), AuditResult.success);
        return u;
    }
}
