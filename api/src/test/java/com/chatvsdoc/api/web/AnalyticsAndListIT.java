package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** T09: {@code GET /api/documents} và {@code POST /api/analytics/events}. */
@IntegrationTest
class AnalyticsAndListIT {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired DocumentRepository docs;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private Cookie sid;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetDocuments();
        user = fixtures.user();
        sid = fixtures.login(mvc, user);
    }

    @Test
    void ac011_1_and_3_listHasAllFieldsCountsEveryStatusNewestFirst() throws Exception {
        User other = fixtures.user();
        Document old = new Document("cu.pdf", 1, "0".repeat(64), "k/1", other.getId());
        old.setStatus(DocumentStatus.failed);
        old.setErrorCode("TOO_MANY_PAGES");
        old.setPageCount(140);
        docs.save(old);
        jdbc.update(
                "UPDATE documents SET uploaded_at = now() - interval '1 day' WHERE id = ?",
                old.getId());
        Document mine = new Document("Hợp đồng.pdf", 1, "0".repeat(64), "k/2", user.getId());
        mine.setStatus(DocumentStatus.ready);
        mine.setPageCount(12);
        mine.setUnreadablePages(new Integer[] {3});
        docs.save(mine);
        Document deleted = new Document("da-xoa.pdf", 1, "0".repeat(64), "k/3", user.getId());
        deleted.setDeletedAt(Instant.now());
        docs.save(deleted);

        mvc.perform(get("/api/documents").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.items[0].name").value("Hợp đồng.pdf"))
                .andExpect(jsonPath("$.items[0].page_count").value(12))
                .andExpect(jsonPath("$.items[0].unreadable_pages[0]").value(3))
                .andExpect(jsonPath("$.items[0].can_delete").value(true))
                .andExpect(jsonPath("$.items[0].uploaded_by.display_name").value("Người dùng thử"))
                .andExpect(jsonPath("$.items[0].uploaded_by.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].error_details").isEmpty())
                .andExpect(jsonPath("$.items[1].status").value("failed"))
                .andExpect(jsonPath("$.items[1].error_code").value("TOO_MANY_PAGES"))
                .andExpect(jsonPath("$.items[1].error_details.page_count").value(140))
                .andExpect(jsonPath("$.items[1].can_delete").value(false));

        User admin = fixtures.user(UserRole.admin, false);
        mvc.perform(get("/api/documents").cookie(fixtures.login(mvc, admin)))
                .andExpect(jsonPath("$.items[1].can_delete").value(true));
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(
                post("/api/analytics/events")
                        .cookie(sid)
                        .header("X-Requested-With", "fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void validBatchIsStoredWithServerSideIdentity() throws Exception {
        String qid = UUID.randomUUID().toString();
        send("{\"events\":[{\"name\":\"chat_answer_first_token\",\"ts\":\""
                        + Instant.now()
                        + "\",\"props\":{\"question_id\":\""
                        + qid
                        + "\",\"ttft_ms\":850}},"
                        + "{\"name\":\"client_network_lost\",\"props\":{\"context\":\"streaming\",\"duration_ms\":0}}]}")
                .andExpect(status().isNoContent());
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT user_id, session_id, props::text AS props FROM events WHERE props->>'question_id' = ?",
                        qid);
        assertThat(row.get("user_id")).isEqualTo(user.getId());
        assertThat(row.get("session_id")).isNotNull();
        assertThat((String) row.get("props"))
                .contains("\"role\": \"member\"")
                .contains("client_ts")
                .contains("app_version");
    }

    @Test
    void anythingOutsideTheWhitelistRejectsTheWholeBatch() throws Exception {
        String[] bad = {
            "{\"events\":[{\"name\":\"auth_login_succeeded\",\"props\":{\"is_first_login\":true}}]}", // chỉ máy chủ phát
            "{\"events\":[{\"name\":\"tu_dat_ten\",\"props\":{}}]}",
            "{\"events\":[{\"name\":\"citation_clicked\",\"props\":{\"question\":\"Thời hạn thanh toán?\"}}]}",
            "{\"events\":[{\"name\":\"citation_clicked\",\"props\":{\"question_id\":\"có khoảng trắng tự do\"}}]}",
            "{\"events\":[{\"name\":\"citation_clicked\",\"props\":{\"question_id\":\""
                    + "a".repeat(65)
                    + "\"}}]}",
            "{\"events\":[{\"name\":\"citation_clicked\",\"props\":{\"user_id\":\""
                    + UUID.randomUUID()
                    + "\"}}]}",
            "{\"events\":[{\"name\":\"client_network_lost\",\"props\":{\"context\":\"khac\"}}]}",
            "{\"events\":[{\"name\":\"chat_answer_first_token\",\"ts\":\"2020-01-01T00:00:00Z\",\"props\":{\"ttft_ms\":1}}]}",
            "{\"events\":[]}",
        };
        for (String body : bad) {
            send(body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        StringBuilder many = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            many.append(i == 0 ? "" : ",")
                    .append(
                            "{\"name\":\"client_network_restored\",\"props\":{\"context\":\"idle\",\"duration_ms\":1}}");
        }
        send(many.append("]}").toString()).andExpect(status().isUnprocessableEntity());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM events WHERE user_id = ? AND name IN ('citation_clicked','tu_dat_ten','client_network_restored')",
                                Integer.class,
                                user.getId()))
                .isZero();
    }

    @Test
    void allThreeEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/analytics/events")
                                .header("X-Requested-With", "fetch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"events\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
