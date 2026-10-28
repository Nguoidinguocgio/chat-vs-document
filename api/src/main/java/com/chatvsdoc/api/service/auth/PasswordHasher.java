package com.chatvsdoc.api.service.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Băm mật khẩu Argon2id (tham số mặc định Spring Security 5.8+: m=16 MiB, t=2, p=1). */
@Component
public class PasswordHasher {

    private final Argon2PasswordEncoder encoder =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && encoder.matches(rawPassword, hash);
    }

    /** Hash cũ dùng tham số yếu hơn mặc định hiện tại → nên băm lại khi đăng nhập đúng. */
    public boolean upgradeEncoding(String hash) {
        return encoder.upgradeEncoding(hash);
    }
}
