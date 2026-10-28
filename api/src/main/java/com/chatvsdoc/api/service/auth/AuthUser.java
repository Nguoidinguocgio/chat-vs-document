package com.chatvsdoc.api.service.auth;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import java.util.UUID;

/** Người dùng đã đăng nhập của yêu cầu hiện tại (principal trong SecurityContext). */
public record AuthUser(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        boolean mustChangePassword,
        UUID sessionId) {

    public static AuthUser of(User u, UUID sessionId) {
        return new AuthUser(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isMustChangePassword(),
                sessionId);
    }

    public boolean isAdmin() {
        return role == UserRole.admin;
    }
}
