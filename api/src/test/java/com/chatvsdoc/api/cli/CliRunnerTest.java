package com.chatvsdoc.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.Session;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.entity.UserStatus;
import com.chatvsdoc.api.domain.repository.AuditLogRepository;
import com.chatvsdoc.api.domain.repository.SessionRepository;
import com.chatvsdoc.api.domain.repository.UserRepository;
import com.chatvsdoc.api.service.auth.PasswordHasher;
import com.chatvsdoc.api.service.auth.UserAdminService;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CliRunner chỉ là bean ở profile {@code cli}; ở đây dựng trực tiếp trên service thật + DB thật.
 */
@IntegrationTest
class CliRunnerTest {

    @Autowired UserAdminService userAdmin;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PasswordHasher hasher;

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final Map<String, String> env = new HashMap<>();
    private CliRunner cli;

    @BeforeEach
    void setUp() {
        cli =
                new CliRunner(
                        userAdmin, new PrintStream(buf, true, StandardCharsets.UTF_8), env::get);
    }

    private String output() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void createUserStoresArgon2idHash() {
        String email = Fixtures.uniqueEmail();
        int code =
                cli.execute(
                        List.of(
                                "--spring.profiles.active=cli",
                                "create-user",
                                "--email",
                                email.toUpperCase(),
                                "--name",
                                "Nguyễn Văn A",
                                "--role",
                                "lead",
                                "--password",
                                "mat-khau-tam-thoi",
                                "--temp"));
        assertThat(code).isZero();

        User u = users.findByEmail(email).orElseThrow();
        assertThat(u.getDisplayName()).isEqualTo("Nguyễn Văn A");
        assertThat(u.getRole()).isEqualTo(UserRole.lead);
        assertThat(u.isMustChangePassword()).isTrue();
        assertThat(u.getPasswordHash()).startsWith("$argon2id$").doesNotContain("mat-khau");
        assertThat(hasher.matches("mat-khau-tam-thoi", u.getPasswordHash())).isTrue();
        assertThat(output()).contains(u.getId().toString()).doesNotContain("mat-khau");
        assertThat(auditLogs.findAll())
                .anySatisfy(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("user.create");
                            assertThat(a.getTarget()).isEqualTo(u.getId().toString());
                        });
    }

    @Test
    void nameAndPasswordCanComeFromEnvironment() {
        String email = Fixtures.uniqueEmail();
        env.put("CLI_NAME", "Trần Thị Bích");
        env.put("CLI_PASSWORD", "tu-bien-moi-truong");
        assertThat(cli.execute(List.of("create-user", "--email", email, "--role", "member")))
                .isZero();
        User u = users.findByEmail(email).orElseThrow();
        assertThat(u.getDisplayName()).isEqualTo("Trần Thị Bích");
        assertThat(hasher.matches("tu-bien-moi-truong", u.getPasswordHash())).isTrue();
    }

    @Test
    void withoutTempFlagPasswordChangeIsNotRequired() {
        String email = Fixtures.uniqueEmail();
        assertThat(
                        cli.execute(
                                List.of(
                                        "create-user",
                                        "--email=" + email,
                                        "--name=B",
                                        "--role=member",
                                        "--password=pw-123456")))
                .isZero();
        assertThat(users.findByEmail(email).orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void rejectsDuplicateEmailInvalidRoleAndMissingArgs() {
        String email = Fixtures.uniqueEmail();
        List<String> create =
                List.of(
                        "create-user",
                        "--email",
                        email,
                        "--name",
                        "C",
                        "--role",
                        "member",
                        "--password",
                        "x1");
        assertThat(cli.execute(create)).isZero();
        assertThat(cli.execute(create)).isEqualTo(1);
        assertThat(
                        cli.execute(
                                List.of(
                                        "create-user",
                                        "--email",
                                        Fixtures.uniqueEmail(),
                                        "--name",
                                        "D",
                                        "--role",
                                        "root",
                                        "--password",
                                        "x1")))
                .isEqualTo(1);
        assertThat(cli.execute(List.of("create-user", "--email", Fixtures.uniqueEmail())))
                .isEqualTo(1);
        assertThat(cli.execute(List.of("unknown"))).isEqualTo(2);
        assertThat(cli.execute(List.of())).isEqualTo(2);
    }

    @Test
    void disableUserRevokesSessions() {
        String email = Fixtures.uniqueEmail();
        User u = userAdmin.createUser(email, "E", UserRole.member, "pw", false);
        String tokenHash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        sessions.save(new Session(tokenHash, u.getId()));

        assertThat(cli.execute(List.of("disable-user", "--email", email))).isZero();
        assertThat(users.findById(u.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.disabled);
        assertThat(sessions.findAll()).noneMatch(s -> s.getUserId().equals(u.getId()));
        assertThat(cli.execute(List.of("disable-user", "--email", Fixtures.uniqueEmail())))
                .isEqualTo(1);
    }
}
