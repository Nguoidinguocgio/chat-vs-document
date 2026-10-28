package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.port.fake.FakeLlm;
import com.chatvsdoc.api.port.fake.FakeLlm.Script;
import com.chatvsdoc.api.service.chat.ChatStreamService;
import com.chatvsdoc.api.support.ApiClient;
import com.chatvsdoc.api.support.ApiClient.SseEvent;
import com.chatvsdoc.api.support.ApiClient.SseStream;
import com.chatvsdoc.api.support.Corpus;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** T14: hội thoại + hỏi đáp SSE với FakeLlm/FakeEmbedding. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ChatControllerIT {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired Corpus corpus;
    @Autowired FakeLlm llm;
    @Autowired ChatStreamService chat;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private ApiClient api;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.resetDocuments();
        llm.setScript(null);
        user = fixtures.user();
        api = new ApiClient(port, fixtures.login(mvc, user).getValue());
        // Mỗi kịch bản có một đoạn chứa đúng từ khóa của câu hỏi để truy hồi vượt ngưỡng.
        corpus.ready(
                user,
                "HopDong_A.pdf",
                "thanh toán trong 30 ngày",
                "doanh thu quý 3",
                "luật pháp áp dụng");
        corpus.ready(
                user,
                "DeXuat_B.pdf",
                "thanh toán trong 45 ngày",
                "ngắt kết nối",
                "trả lời chậm",
                "lỗi dịch vụ",
                "injection thử");
    }

    @AfterEach
    void tearDown() {
        llm.setScript(null);
    }

    private String conversation() throws Exception {
        return api.post("/api/conversations").json().path("id").asText();
    }

    private static String body(String q) {
        return "{\"question\":" + ApiClient.JSON.valueToTree(q) + "}";
    }

    /** Đọc tới khi luồng đóng (tối đa 10 s), trả mọi sự kiện. */
    private static List<SseEvent> all(SseStream s) throws Exception {
        List<SseEvent> out = new ArrayList<>();
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            out.addAll(s.drain(100));
            if (s.awaitEnd(1)) {
                out.addAll(s.drain(100));
                break;
            }
        }
        return out;
    }

    private static List<String> names(List<SseEvent> ev) {
        return ev.stream().map(SseEvent::name).toList();
    }

    private static JsonNode data(SseEvent e) {
        try {
            return ApiClient.JSON.readTree(e.data());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> message(String id) {
        return jdbc.queryForMap("SELECT * FROM messages WHERE id = ?::uuid", id);
    }

    @Test
    void ac020_1_orderMetaStatusDeltasCitationsDoneAndPersisted() throws Exception {
        String conv = conversation();
        List<SseEvent> ev;
        try (SseStream s =
                api.postSse(
                        "/api/conversations/" + conv + "/messages",
                        body("Thời hạn thanh toán bao lâu?"))) {
            assertThat(s.status).isEqualTo(200);
            ev = all(s);
        }
        List<String> n = names(ev);
        assertThat(n.subList(0, 2)).containsExactly("meta", "status");
        assertThat(n).contains("delta", "citation");
        assertThat(n.get(n.size() - 1)).isEqualTo("done");
        assertThat(n.stream().filter(x -> x.equals("done") || x.equals("error"))).hasSize(1);
        assertThat(data(ev.get(ev.size() - 1)).path("answer_type").asText()).isEqualTo("answered");

        String messageId = data(ev.get(0)).path("message_id").asText();
        assertThat(data(ev.get(0)).path("lang").asText()).isEqualTo("vi");
        List<JsonNode> cites =
                ev.stream()
                        .filter(e -> e.name().equals("citation"))
                        .map(ChatControllerIT::data)
                        .toList();
        assertThat(cites).extracting(c -> c.path("index").asInt()).containsExactly(1, 2);
        assertThat(cites.get(0).path("document_deleted").asBoolean()).isFalse();
        assertThat(cites)
                .extracting(c -> c.path("document_name").asText())
                .allMatch(x -> x.endsWith(".pdf"));

        Map<String, Object> m = message(messageId);
        assertThat(m.get("status").toString()).isEqualTo("completed");
        assertThat(m.get("answer").toString()).contains("[1]").contains("[2]");
        assertThat(m.get("ttft_ms")).isNotNull();
        assertThat(m.get("total_ms")).isNotNull();

        // AC-022.1: trích dẫn lưu đúng tài liệu/trang, GET hội thoại khớp luồng đã phát
        JsonNode detail = api.get("/api/conversations/" + conv).json();
        JsonNode saved = detail.path("messages").get(0);
        assertThat(saved.path("status").asText()).isEqualTo("completed");
        assertThat(saved.path("citations")).hasSize(2);
        assertThat(saved.path("citations").get(0).path("page").asInt())
                .isEqualTo(cites.get(0).path("page").asInt());
        assertThat(saved.path("citations").get(0).path("document_id").asText())
                .isEqualTo(cites.get(0).path("document_id").asText());
        assertThat(detail.path("conversation").path("title").asText())
                .isEqualTo("Thời hạn thanh toán bao lâu?");
    }

    @Test
    void ac020_2_everyScenarioTerminates() throws Exception {
        Map<String, String> expected =
                Map.of(
                        "doanh thu quý 3", "done:partial",
                        "luật pháp áp dụng", "done:not_found",
                        "trả lời chậm", "error:timeout",
                        "lỗi dịch vụ", "error:llm_error");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String conv = conversation();
            List<SseEvent> ev;
            try (SseStream s =
                    api.postSse("/api/conversations/" + conv + "/messages", body(e.getKey()))) {
                ev = all(s);
            }
            SseEvent last = ev.get(ev.size() - 1);
            String got =
                    last.name()
                            + ":"
                            + (last.name().equals("done")
                                    ? data(last).path("answer_type").asText()
                                    : data(last).path("code").asText());
            assertThat(got).as(e.getKey()).isEqualTo(e.getValue());
            assertThat(names(ev).stream().filter(x -> x.equals("done") || x.equals("error")))
                    .as(e.getKey())
                    .hasSize(1);
        }
        JsonNode partial = null;
        for (JsonNode c : api.get("/api/conversations").json()) {
            if (c.path("title").asText().equals("doanh thu quý 3")) {
                partial =
                        api.get("/api/conversations/" + c.path("id").asText())
                                .json()
                                .path("messages")
                                .get(0);
            }
        }
        assertThat(partial.path("missing").asText()).startsWith("Biên lợi nhuận");
    }

    @Test
    void connectionDropScenarioClosesStreamWithoutTerminalEvent() throws Exception {
        String conv = conversation();
        List<SseEvent> ev;
        try (SseStream s =
                api.postSse("/api/conversations/" + conv + "/messages", body("ngắt kết nối"))) {
            ev = all(s);
        }
        assertThat(names(ev)).contains("delta").doesNotContain("done", "error");
        String id = data(ev.get(0)).path("message_id").asText();
        Map<String, Object> m = message(id);
        assertThat(m.get("status").toString()).isEqualTo("failed");
        assertThat(m.get("error_code")).isEqualTo("llm_error");
    }

    @Test
    void ac023_1_emptyRetrievalAnswersNotFoundWithoutCallingLlm() throws Exception {
        int before = llm.calls();
        String conv = conversation();
        List<SseEvent> ev;
        try (SseStream s =
                api.postSse("/api/conversations/" + conv + "/messages", body("Mấy giờ rồi nhỉ"))) {
            ev = all(s);
        }
        assertThat(names(ev)).containsExactly("meta", "status", "done");
        assertThat(data(ev.get(2)).path("answer_type").asText()).isEqualTo("not_found");
        assertThat(llm.calls()).isEqualTo(before);
    }

    @Test
    void validationErrorsAreJsonBeforeTheStreamOpens() throws Exception {
        String conv = conversation();
        String path = "/api/conversations/" + conv + "/messages";
        assertThat(api.postJson(path, body("   ")).json().path("error").path("code").asText())
                .isEqualTo("QUESTION_INVALID");
        assertThat(api.postJson(path, body("a".repeat(2001))).status()).isEqualTo(422);
        try (SseStream s = api.postSse(path, body("thanh toán " + "a".repeat(1989)))) {
            assertThat(s.status).isEqualTo(200);
            all(s);
        }
        assertThat(
                        api.postJson(
                                        "/api/conversations/" + UUID.randomUUID() + "/messages",
                                        body("x"))
                                .status())
                .isEqualTo(404);

        fixtures.resetDocuments();
        ApiClient.Response noDocs = api.postJson(path, body("thanh toán"));
        assertThat(noDocs.status()).isEqualTo(409);
        assertThat(noDocs.json().path("error").path("code").asText())
                .isEqualTo("NO_READY_DOCUMENTS");
    }

    @Test
    void secondQuestionWhileStreamingIsAnswerInProgress() throws Exception {
        llm.setScript(Script.of("a ", "b ", "c [1]").withDelay(Duration.ofMillis(500)));
        String conv = conversation();
        String path = "/api/conversations/" + conv + "/messages";
        try (SseStream first = api.postSse(path, body("thanh toán"))) {
            first.next("meta", 3000);
            ApiClient.Response second = api.postJson(path, body("thanh toán lần 2"));
            assertThat(second.status()).isEqualTo(409);
            assertThat(second.json().path("error").path("code").asText())
                    .isEqualTo("ANSWER_IN_PROGRESS");
            all(first);
        }
    }

    @Test
    void clientDisconnectCancelsGenerationAndKeepsPartialAnswer() throws Exception {
        llm.setScript(
                Script.of("Phần ", "đầu ", "đã ", "có ", "rồi ", "còn ", "nữa")
                        .withDelay(Duration.ofMillis(300)));
        String conv = conversation();
        String messageId;
        SseStream s = api.postSse("/api/conversations/" + conv + "/messages", body("thanh toán"));
        messageId = data(s.next("meta", 3000)).path("message_id").asText();
        s.next("delta", 3000);
        s.close();
        String status = "streaming";
        for (int i = 0; i < 60 && status.equals("streaming"); i++) {
            Fixtures.sleep(100);
            status = message(messageId).get("status").toString();
        }
        assertThat(status).isEqualTo("stopped");
        assertThat(message(messageId).get("answer").toString()).startsWith("Phần");
        for (int i = 0; i < 30 && chat.activeStreams() > 0; i++) {
            Fixtures.sleep(100);
        }
        assertThat(chat.activeStreams()).isZero();
    }

    @Test
    void othersConversationsAreNotFoundEvenForAdmin() throws Exception {
        String conv = conversation();
        User admin = fixtures.user(UserRole.admin, false);
        ApiClient other = new ApiClient(port, fixtures.login(mvc, admin).getValue());
        assertThat(other.get("/api/conversations/" + conv).status()).isEqualTo(404);
        assertThat(
                        other.postJson(
                                        "/api/conversations/" + conv + "/messages",
                                        body("thanh toán"))
                                .status())
                .isEqualTo(404);
        assertThat(other.get("/api/conversations").json()).isEmpty();
        assertThat(api.get("/api/conversations").json()).hasSize(1);
    }

    @Test
    void orphanedStreamingMessagesAreFailedOnStartup() throws Exception {
        String conv = conversation();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO messages (id, conversation_id, question, lang, status) VALUES (?, ?::uuid, 'q', 'vi', 'streaming')",
                id,
                conv);
        chat.failOrphanedStreams();
        Map<String, Object> m =
                jdbc.queryForMap(
                        "SELECT status::text AS s, error_code FROM messages WHERE id = ?", id);
        assertThat(m.get("s")).isEqualTo("failed");
        assertThat(m.get("error_code")).isEqualTo("llm_error");
    }

    @Test
    void analyticsEventsHaveNoContentAndLogsHaveNoQuestionOrAnswer(CapturedOutput output)
            throws Exception {
        String conv = conversation();
        try (SseStream s =
                api.postSse(
                        "/api/conversations/" + conv + "/messages",
                        body("Thời hạn thanh toán XYZ-BIMAT?"))) {
            all(s);
        }
        try (SseStream s =
                api.postSse("/api/conversations/" + conv + "/messages", body("lỗi dịch vụ"))) {
            all(s);
        }
        List<String> events =
                jdbc.queryForList(
                        "SELECT name || ' ' || props::text FROM events WHERE user_id = ? AND name LIKE 'chat_%' ORDER BY id",
                        String.class, user.getId());
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("chat_question_submitted")
                                        && e.contains("\"turn_index\": 0")
                                        && e.contains("\"question_lang\": \"vi\""));
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("chat_question_submitted")
                                        && e.contains("\"turn_index\": 1"));
        assertThat(events)
                .anyMatch(
                        e ->
                                e.startsWith("chat_answer_completed")
                                        && e.contains("\"citation_count\": 2")
                                        && e.contains("\"distinct_document_count\": 2"));
        assertThat(events)
                .anyMatch(e -> e.startsWith("chat_answer_failed") && e.contains("llm_error"));
        assertThat(String.join(" ", events))
                .doesNotContain("XYZ-BIMAT")
                .doesNotContain("không thống nhất");
        assertThat(output.getAll())
                .doesNotContain("XYZ-BIMAT")
                .doesNotContain("không thống nhất")
                .doesNotContain("30 ngày");
    }

    @Test
    void requiresLoginAndCsrfHeader() throws Exception {
        assertThat(new ApiClient(port, null).get("/api/conversations").status()).isEqualTo(401);
        String conv = conversation();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/conversations/" + conv + "/messages")
                                .cookie(fixtures.login(mvc, user))
                                .contentType("application/json")
                                .content(body("thanh toán")))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isForbidden());
    }
}
