package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.auth.AuthService;
import com.chatvsdoc.api.service.auth.TokenGenerator;
import com.chatvsdoc.api.service.auth.UserAdminService;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.chatvsdoc.api.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** T04: đăng nhập, phiên, khóa tạm, mật khẩu tạm, vô hiệu hóa. */
@IntegrationTest
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired MutableClock clock;
    @Autowired UserRepository users;
    @Autowired UserAdminService userAdmin;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(
                post("/api/auth/login")
                        .header("X-Requested-With", "fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void ac001_1_loginSetsSecureCookieAndMeReturnsUser() throws Exception {
        User u = fixtures.user(UserRole.lead, false);
        MvcResult r =
                login("  " + u.getEmail().toUpperCase() + " ", Fixtures.PASSWORD)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.email").value(u.getEmail()))
                        .andExpect(jsonPath("$.role").value("lead"))
                        .andExpect(jsonPath("$.must_change_password").value(false))
                        .andReturn();
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("sid=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/");
        Cookie sid = r.getResponse().getCookie("sid");
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.getId().toString()))
                .andExpect(jsonPath("$.display_name").value("Người dùng thử"));

        // Chỉ lưu SHA-256 của token, không lưu token gốc.
        List<String> hashes =
                jdbc.queryForList(
                        "SELECT token_hash FROM sessions WHERE user_id = ?",
                        String.class,
                        u.getId());
        assertThat(hashes).containsExactly(TokenGenerator.sha256Hex(sid.getValue()));
        assertThat(users.findById(u.getId()).orElseThrow().getPasswordHash())
                .startsWith("$argon2id$");
    }

    @Test
    void ac001_2_wrongEmailAndWrongPasswordGiveIdenticalResponse() throws Exception {
        User u = fixtures.user();
        String a =
                login("khong-ton-tai@example.test", Fixtures.PASSWORD)
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String b =
                login(u.getEmail(), "sai-mat-khau-1")
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(a)
                .isEqualTo(b)
                .contains("INVALID_CREDENTIALS")
                .contains("auth.invalid_credentials");
    }

    @Test
    void ac001_2_timingIsComparable() throws Exception {
        User u = fixtures.user();
        long unknown = 0;
        long wrong = 0;
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            login("x-" + i + "@example.test", "abc");
            unknown += System.nanoTime() - t0;
            t0 = System.nanoTime();
            login(u.getEmail(), "wrong-" + i);
            wrong += System.nanoTime() - t0;
        }
        // Cả hai nhánh đều chạy Argon2: chênh lệch không quá 2 lần.
        assertThat((double) Math.max(unknown, wrong) / Math.min(unknown, wrong)).isLessThan(2.0);
    }

    @Test
    void ac001_3_fiveFailuresLockForFifteenMinutes() throws Exception {
        User u = fixtures.user();
        for (int i = 0; i < 5; i++) {
            login(u.getEmail(), "sai-" + i).andExpect(status().isUnauthorized());
        }
        login(u.getEmail(), Fixtures.PASSWORD)
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.error.details.retry_after_s").isNumber());
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        login(u.getEmail(), Fixtures.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() throws Exception {
        User u = fixtures.user();
        for (int i = 0; i < 4; i++) {
            login(u.getEmail(), "sai").andExpect(status().isUnauthorized());
        }
        clock.advance(Duration.ofMinutes(16));
        login(u.getEmail(), "sai").andExpect(status().isUnauthorized());
        login(u.getEmail(), Fixtures.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void concurrentFailuresNeverOvershootCounter() throws Exception {
        User u = fixtures.user();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            calls.add(
                    () -> {
                        try {
                            auth.login(u.getEmail(), "sai-song-song");
                        } catch (RuntimeException expected) {
                            // INVALID_CREDENTIALS hoặc ACCOUNT_LOCKED
                        }
                        return null;
                    });
        }
        pool.invokeAll(calls);
        pool.shutdown();
        User after = users.findById(u.getId()).orElseThrow();
        assertThat(after.getFailedAttempts()).isBetween(0, 4);
        assertThat(after.getLockedUntil()).isNotNull();
    }

    @Test
    void ac001_4_mustChangePasswordBlocksOtherApisUntilChanged() throws Exception {
        User u = fixtures.user(UserRole.member, true);
        Cookie sid = fixtures.login(mvc, u);
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.must_change_password").value(true));
        mvc.perform(get("/api/documents").cookie(sid))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        // Không đạt chính sách → 422 auth.password_policy
        for (String bad : new String[] {"ngan1", "chikhongcoso", "1234567890", Fixtures.PASSWORD}) {
            changePassword(sid, null, bad)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message_key").value("auth.password_policy"));
        }
        changePassword(sid, null, "MatKhauMoi-2026").andExpect(status().isNoContent());
        mvc.perform(get("/api/documents").cookie(sid)).andExpect(status().isOk());
    }

    @Test
    void changePasswordRequiresCurrentAndRevokesOtherSessions() throws Exception {
        User u = fixtures.user();
        Cookie first = fixtures.login(mvc, u);
        Cookie second = fixtures.login(mvc, u);
        changePassword(first, "sai-hien-tai", "MatKhauMoi-2026")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        changePassword(first, Fixtures.PASSWORD, "MatKhauMoi-2026")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(first)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(second))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    private ResultActions changePassword(Cookie sid, String current, String next) throws Exception {
        String body =
                current == null
                        ? "{\"new_password\":\"" + next + "\"}"
                        : "{\"current_password\":\""
                                + current
                                + "\",\"new_password\":\""
                                + next
                                + "\"}";
        return mvc.perform(
                post("/api/auth/change-password")
                        .cookie(sid)
                        .header("X-Requested-With", "fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void ac001_5_idleEightHoursExpiresSession() throws Exception {
        User u = fixtures.user();
        Cookie sid = fixtures.login(mvc, u);
        clock.advance(Duration.ofHours(7).plusMinutes(59));
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isOk());
        // Mỗi yêu cầu hợp lệ gia hạn: rảnh tiếp 8 giờ + 1 phút mới hết hạn.
        clock.advance(Duration.ofHours(8).plusMinutes(1));
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        // Trang đăng nhập vẫn dùng được khi cookie đã hết hạn.
        login(u.getEmail(), Fixtures.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac001_6_protectedEndpointsWithoutCookieAre401() throws Exception {
        for (String path : new String[] {"/api/me", "/api/documents", "/api/events"}) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        }
        mvc.perform(get("/api/me").cookie(new Cookie("sid", "token-gia")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ac003_disabledUserLosesSessionAndGetsAccountDisabled() throws Exception {
        User u = fixtures.user();
        Cookie sid = fixtures.login(mvc, u);
        // Vô hiệu hóa xóa phiên ngay (revokeUserSessions) → cookie cũ không còn phiên: 401.
        userAdmin.disableUser(u.getEmail());
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isUnauthorized());
        // Nếu phiên còn sót (vô hiệu hóa trực tiếp trong DB), lần gọi kế tiếp trả SESSION_EXPIRED.
        User v = fixtures.user();
        Cookie sid2 = fixtures.login(mvc, v);
        jdbc.update("UPDATE users SET status = 'disabled' WHERE id = ?", v.getId());
        mvc.perform(get("/api/me").cookie(sid2))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        login(u.getEmail(), Fixtures.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
        // Sai mật khẩu thì vẫn chỉ là INVALID_CREDENTIALS (không lộ trạng thái).
        login(u.getEmail(), "sai-mat-khau").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeletesSessionAndClearsCookie() throws Exception {
        User u = fixtures.user();
        Cookie sid = fixtures.login(mvc, u);
        String setCookie =
                mvc.perform(
                                post("/api/auth/logout")
                                        .cookie(sid)
                                        .header("X-Requested-With", "fetch"))
                        .andExpect(status().isNoContent())
                        .andReturn()
                        .getResponse()
                        .getHeader("Set-Cookie");
        assertThat(setCookie).contains("Max-Age=0");
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsAuditedAndTrackedWithoutPassword() throws Exception {
        User u = fixtures.user();
        login(u.getEmail(), "sai-mat-khau-XYZ");
        login(u.getEmail(), Fixtures.PASSWORD);
        List<String> results =
                jdbc.queryForList(
                        "SELECT result::text FROM audit_logs WHERE action = 'auth.login' AND actor_user_id = ? ORDER BY id",
                        String.class,
                        u.getId());
        assertThat(results).containsExactly("failure", "success");
        List<String> events =
                jdbc.queryForList(
                        "SELECT name || ' ' || props::text FROM events WHERE user_id = ? ORDER BY id",
                        String.class,
                        u.getId());
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("auth_login_failed")
                                        && e.contains("invalid_credentials"));
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("auth_login_succeeded")
                                        && e.contains("\"is_first_login\": true"));
        assertThat(String.join(" ", events))
                .doesNotContain("XYZ")
                .doesNotContain(Fixtures.PASSWORD);
    }
}
