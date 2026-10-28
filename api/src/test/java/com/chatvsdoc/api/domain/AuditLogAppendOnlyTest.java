package com.chatvsdoc.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatvsdoc.api.domain.entity.AuditResult;
import com.chatvsdoc.api.domain.repository.AuditLogRepository;
import com.chatvsdoc.api.service.audit.AuditService;
import com.chatvsdoc.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Role ứng dụng (cả khi là chủ bảng/superuser như trong test) chỉ INSERT được vào audit_logs. */
@IntegrationTest
class AuditLogAppendOnlyTest {

    @Autowired AuditService audit;
    @Autowired AuditLogRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void insertWorksButUpdateDeleteTruncateAreRejected() {
        audit.record(null, "test.append_only", "t-1", AuditResult.success);
        Long id =
                jdbc.queryForObject(
                        "SELECT max(id) FROM audit_logs WHERE action = 'test.append_only'",
                        Long.class);
        assertThat(id).isNotNull();

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE audit_logs SET result = 'failure' WHERE id = ?",
                                        id))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> repo.deleteById(id)).isInstanceOf(DataAccessException.class);

        assertThat(repo.findById(id))
                .get()
                .extracting(a -> a.getResult())
                .isEqualTo(AuditResult.success);
    }

    @Test
    void privilegesAreRevokedForApplicationRole() {
        Boolean canUpdate =
                jdbc.queryForObject(
                        "SELECT has_table_privilege(current_user, 'audit_logs', 'UPDATE') "
                                + "AND NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user)",
                        Boolean.class);
        assertThat(canUpdate).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.role_table_grants "
                                        + "WHERE table_name = 'audit_logs' AND grantee = current_user "
                                        + "AND privilege_type IN ('UPDATE', 'DELETE', 'TRUNCATE')",
                                Integer.class))
                .isZero();
    }
}
