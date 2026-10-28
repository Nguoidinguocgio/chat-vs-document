package com.chatvsdoc.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.auth.PasswordHasher;
import com.chatvsdoc.api.service.documents.InProcessJobQueue;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dữ liệu mẫu cho test; mỗi lần gọi sinh email riêng để các test không đụng nhau trên DB dùng
 * chung.
 */
@TestComponent
public class Fixtures {

    public static final String PASSWORD = "Matkhau-dung-123";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final JdbcTemplate jdbc;
    private final InProcessJobQueue queue;
    // Băm một lần cho mọi người dùng mẫu (Argon2 chậm có chủ đích).
    private String passwordHash;

    public Fixtures(
            UserRepository users,
            PasswordHasher hasher,
            JdbcTemplate jdbc,
            InProcessJobQueue queue) {
        this.users = users;
        this.hasher = hasher;
        this.jdbc = jdbc;
        this.queue = queue;
    }

    public static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.test";
    }

    public User user() {
        return user(UserRole.member, false);
    }

    /** Người dùng có mật khẩu {@link #PASSWORD}. */
    public User user(UserRole role, boolean mustChangePassword) {
        if (passwordHash == null) {
            passwordHash = hasher.hash(PASSWORD);
        }
        User u = new User(uniqueEmail(), "Người dùng thử", passwordHash, role);
        u.setMustChangePassword(mustChangePassword);
        return users.save(u);
    }

    /** Đăng nhập qua MockMvc, trả cookie {@code sid}. */
    public Cookie login(MockMvc mvc, User u) throws Exception {
        return mvc.perform(
                        post("/api/auth/login")
                                .header("X-Requested-With", "fetch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + u.getEmail()
                                                + "\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andReturn()
                .getResponse()
                .getCookie("sid");
    }

    /** Chờ hàng đợi xử lý rảnh rồi xóa mọi tài liệu (test cần đếm chính xác số tài liệu). */
    public void resetDocuments() {
        awaitQueueIdle();
        jdbc.update("DELETE FROM citations");
        jdbc.update("DELETE FROM documents");
    }

    public void awaitQueueIdle() {
        Instant until = Instant.now().plus(Duration.ofSeconds(30));
        while (queue.pendingCount() > 0 && Instant.now().isBefore(until)) {
            sleep(20);
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
