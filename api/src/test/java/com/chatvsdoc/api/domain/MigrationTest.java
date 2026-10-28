package com.chatvsdoc.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Chạy migration trên một database trống riêng (không đụng DB mà các test khác đang dùng): migrate
 * tạo đủ bảng, rồi clean + migrate lại vẫn chạy được.
 */
@IntegrationTest
class MigrationTest {

    private static final List<String> TABLES =
            List.of(
                    "users",
                    "sessions",
                    "documents",
                    "document_pages",
                    "chunks",
                    "conversations",
                    "messages",
                    "citations",
                    "audit_logs",
                    "events");

    @Autowired PostgreSQLContainer<?> postgres;

    private static DriverManagerDataSource fresh;

    // Tạo lười vì cần container (bean của context) đã chạy.
    private DriverManagerDataSource freshDb() throws Exception {
        if (fresh == null) {
            try (Connection c =
                            DriverManager.getConnection(
                                    postgres.getJdbcUrl(),
                                    postgres.getUsername(),
                                    postgres.getPassword());
                    Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE migration_test");
            }
            String url =
                    postgres.getJdbcUrl()
                            .replace("/" + postgres.getDatabaseName(), "/migration_test");
            fresh =
                    new DriverManagerDataSource(
                            url, postgres.getUsername(), postgres.getPassword());
        }
        return fresh;
    }

    private Flyway flyway() throws Exception {
        return Flyway.configure()
                .dataSource(freshDb())
                .locations("classpath:db/migration")
                .placeholders(Map.of("embeddingDim", "1024"))
                .cleanDisabled(false)
                .load();
    }

    @Test
    void migrateCreatesAllTablesThenCleanAndMigrateAgain() throws Exception {
        Flyway flyway = flyway();
        flyway.migrate();
        assertSchema(new JdbcTemplate(freshDb()));

        flyway.clean();
        assertThat(
                        new JdbcTemplate(freshDb())
                                .queryForList(
                                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                                        String.class))
                .isEmpty();

        flyway.migrate();
        assertSchema(new JdbcTemplate(freshDb()));
    }

    private static void assertSchema(JdbcTemplate jdbc) {
        assertThat(
                        jdbc.queryForList(
                                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                                String.class))
                .containsAll(TABLES);
        assertThat(jdbc.queryForList("SELECT extname FROM pg_extension", String.class))
                .contains("vector", "citext");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT format_type(atttypid, atttypmod) FROM pg_attribute "
                                        + "WHERE attrelid = 'chunks'::regclass AND attname = 'embedding'",
                                String.class))
                .isEqualTo("vector(1024)");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT indexdef FROM pg_indexes WHERE indexname = 'chunks_embedding_hnsw_idx'",
                                String.class))
                .contains("hnsw")
                .contains("vector_cosine_ops");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT data_type FROM information_schema.columns "
                                        + "WHERE table_name = 'users' AND column_name = 'email'",
                                String.class))
                .isEqualTo("USER-DEFINED");
    }
}
