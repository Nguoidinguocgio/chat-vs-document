package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chatvsdoc.api.domain.entity.Document;
import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.domain.repository.DocumentRepository;
import com.chatvsdoc.api.support.ApiClient;
import com.chatvsdoc.api.support.Corpus;
import com.chatvsdoc.api.support.Fixtures;
import com.chatvsdoc.api.support.IntegrationTest;
import com.chatvsdoc.api.support.MakeFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.servlet.http.Cookie;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T06: backend khớp {@code contracts/openapi.yaml}. (1) Mọi route {@code /api/**} đã hiện thực có
 * trong hợp đồng (đối chiếu {@code /v3/api-docs} do springdoc sinh). (2) Phản hồi thật của các
 * endpoint đã hiện thực ({@code x-implemented-in}) hợp lệ theo schema của hợp đồng.
 */
@IntegrationTest
class ContractTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode spec;

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired DocumentRepository docs;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void loadSpec() throws Exception {
        spec = YAML.readTree(new File("../contracts/openapi.yaml"));
    }

    /** Schema phản hồi theo hợp đồng, chuyển {@code #/components/schemas} thành {@code $defs}. */
    private static JsonSchema responseSchema(String method, String path, int status) {
        JsonNode op = spec.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        assertThat(op.isMissingNode()).as(method + " " + path + " có trong hợp đồng").isFalse();
        JsonNode response = op.path("responses").path(String.valueOf(status));
        assertThat(response.isMissingNode())
                .as(method + " " + path + " khai báo " + status)
                .isFalse();
        if (response.has("$ref")) {
            String name = response.get("$ref").asText().replace("#/components/responses/", "");
            response = spec.path("components").path("responses").path(name);
        }
        JsonNode schema = response.path("content").path("application/json").path("schema");
        assertThat(schema.isMissingNode())
                .as(method + " " + path + " " + status + " có schema JSON")
                .isFalse();
        ObjectNode root = (ObjectNode) rewriteRefs(schema.deepCopy());
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.set("$defs", rewriteRefs(spec.path("components").path("schemas").deepCopy()));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
    }

    private static JsonNode rewriteRefs(JsonNode node) {
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            if (o.has("$ref")) {
                o.put("$ref", o.get("$ref").asText().replace("#/components/schemas/", "#/$defs/"));
            }
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            while (it.hasNext()) {
                rewriteRefs(it.next().getValue());
            }
        } else if (node.isArray()) {
            node.forEach(ContractTest::rewriteRefs);
        }
        return node;
    }

    private final List<String> violations = new ArrayList<>();

    private void check(String method, String path, int expectedStatus, int status, String body)
            throws Exception {
        assertThat(status).as(method + " " + path).isEqualTo(expectedStatus);
        Set<ValidationMessage> errors =
                responseSchema(method, path, status).validate(JSON.readTree(body));
        errors.forEach(
                e -> violations.add(method + " " + path + " " + status + ": " + e.getMessage()));
    }

    private void check(String method, String path, int expected, MockHttpServletRequestBuilder req)
            throws Exception {
        MvcResult r = mvc.perform(req).andReturn();
        check(
                method,
                path,
                expected,
                r.getResponse().getStatus(),
                r.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder b, String body) {
        return b.header("X-Requested-With", "fetch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void validatorRejectsResponsesThatDriftFromTheContract() throws Exception {
        JsonSchema me = responseSchema("GET", "/api/me", 200);
        assertThat(me.validate(JSON.readTree("{\"id\":\"x\"}"))).isNotEmpty();
        assertThat(
                        me.validate(
                                JSON.readTree(
                                        "{\"id\":\"8f8f8f8f-1111-4222-8333-444455556666\",\"email\":\"a@b.vn\","
                                                + "\"display_name\":\"A\",\"role\":\"root\",\"must_change_password\":false}")))
                .as("role ngoài enum")
                .isNotEmpty();
        assertThat(
                        me.validate(
                                JSON.readTree(
                                        "{\"id\":\"8f8f8f8f-1111-4222-8333-444455556666\",\"email\":\"a@b.vn\","
                                                + "\"display_name\":\"A\",\"role\":\"admin\",\"must_change_password\":false,"
                                                + "\"password_hash\":\"x\"}")))
                .as("trường lạ (additionalProperties: false)")
                .isNotEmpty();
    }

    @Test
    void everyImplementedApiRouteIsInTheContract() throws Exception {
        JsonNode generated =
                JSON.readTree(
                        mvc.perform(get("/v3/api-docs"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<String> missing = new ArrayList<>();
        generated
                .path("paths")
                .fields()
                .forEachRemaining(
                        p -> {
                            if (!p.getKey().startsWith("/api/")
                                    || p.getKey().startsWith("/api/test/")) {
                                return;
                            }
                            p.getValue()
                                    .fieldNames()
                                    .forEachRemaining(
                                            method -> {
                                                if (spec.path("paths")
                                                        .path(p.getKey())
                                                        .path(method)
                                                        .isMissingNode()) {
                                                    missing.add(
                                                            method.toUpperCase(Locale.ROOT)
                                                                    + " "
                                                                    + p.getKey());
                                                }
                                            });
                        });
        assertThat(missing)
                .as("route có trong mã nhưng thiếu trong contracts/openapi.yaml")
                .isEmpty();
    }

    @Test
    void implementedResponsesMatchSchemas() throws Exception {
        fixtures.resetDocuments();
        User u = fixtures.user(UserRole.admin, false);
        String body =
                "{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}";
        MvcResult login = mvc.perform(json(post("/api/auth/login"), body)).andReturn();
        check(
                "POST",
                "/api/auth/login",
                200,
                login.getResponse().getStatus(),
                login.getResponse().getContentAsString());
        Cookie sid = login.getResponse().getCookie("sid");

        check(
                "POST",
                "/api/auth/login",
                401,
                json(post("/api/auth/login"), "{\"email\":\"x@y.z\",\"password\":\"sai\"}"));
        check("POST", "/api/auth/login", 422, json(post("/api/auth/login"), "{\"email\":\"\"}"));
        check("GET", "/api/me", 200, get("/api/me").cookie(sid));
        check("GET", "/api/me", 401, get("/api/me"));
        check(
                "POST",
                "/api/auth/change-password",
                422,
                json(
                        post("/api/auth/change-password").cookie(sid),
                        "{\"current_password\":\""
                                + Fixtures.PASSWORD
                                + "\",\"new_password\":\"ngan\"}"));

        Document failed =
                new Document("qua-dai.pdf", 1, "0".repeat(64), "k/" + UUID.randomUUID(), u.getId());
        failed.setStatus(DocumentStatus.failed);
        failed.setErrorCode("TOO_MANY_PAGES");
        failed.setPageCount(120);
        docs.save(failed);
        ApiClient api = new ApiClient(port, sid.getValue());
        ApiClient.Response created = api.upload("Hợp đồng.pdf", MakeFixtures.okEn());
        check("POST", "/api/documents", 201, created.status(), created.body());
        ApiClient.Response notPdf = api.upload("a.pdf", "khong phai pdf".getBytes());
        check("POST", "/api/documents", 400, notPdf.status(), notPdf.body());
        fixtures.awaitQueueIdle();
        check("GET", "/api/documents", 200, get("/api/documents").cookie(sid));

        ApiClient.Response notRetryable = api.post("/api/documents/" + failed.getId() + "/retry");
        check("POST", "/api/documents/{id}/retry", 409, notRetryable.status(), notRetryable.body());
        jdbc.update(
                "UPDATE documents SET error_code = 'SYSTEM_ERROR' WHERE id = ?", failed.getId());
        ApiClient.Response retried = api.post("/api/documents/" + failed.getId() + "/retry");
        check("POST", "/api/documents/{id}/retry", 202, retried.status(), retried.body());

        check(
                "POST",
                "/api/analytics/events",
                422,
                json(
                        post("/api/analytics/events").cookie(sid),
                        "{\"events\":[{\"name\":\"khong_co\"}]}"));
        fixtures.awaitQueueIdle();

        assertThat(violations).isEmpty();
    }

    @Autowired Corpus corpus;

    /** Schema của một component (dùng cho sự kiện SSE khai báo ở {@code x-sse-events}). */
    private static JsonSchema componentSchema(String name) {
        ObjectNode root = JSON.createObjectNode();
        root.put("$ref", "#/$defs/" + name);
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.set("$defs", rewriteRefs(spec.path("components").path("schemas").deepCopy()));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
    }

    @Test
    void conversationResponsesAndAnswerEventsMatchSchemas() throws Exception {
        fixtures.resetDocuments();
        User u = fixtures.user();
        Cookie sid = fixtures.login(mvc, u);
        ApiClient api = new ApiClient(port, sid.getValue());
        corpus.ready(u, "HopDong.pdf", "thanh toán trong 30 ngày", "doanh thu quý 3");

        ApiClient.Response created = api.post("/api/conversations");
        check("POST", "/api/conversations", 201, created.status(), created.body());
        String id = created.json().path("id").asText();
        String path = "/api/conversations/" + id + "/messages";
        check(
                "POST",
                "/api/conversations/{id}/messages",
                422,
                json(post(path).cookie(sid), "{\"question\":\"\"}"));

        JsonNode declared =
                spec.path("paths")
                        .path("/api/conversations/{id}/messages")
                        .path("post")
                        .path("x-sse-events");
        for (String q : List.of("thanh toán", "doanh thu", "lỗi dịch vụ")) {
            try (ApiClient.SseStream s = api.postSse(path, "{\"question\":\"" + q + "\"}")) {
                assertThat(s.status).isEqualTo(200);
                List<ApiClient.SseEvent> got = new ArrayList<>();
                for (int i = 0; i < 100 && !s.awaitEnd(100); i++) {
                    got.addAll(s.drain(50));
                }
                got.addAll(s.drain(100));
                assertThat(got).isNotEmpty();
                for (ApiClient.SseEvent e : got) {
                    String ref = declared.path(e.name()).path("$ref").asText();
                    assertThat(ref)
                            .as("sự kiện " + e.name() + " có trong x-sse-events")
                            .isNotEmpty();
                    componentSchema(ref.replace("#/components/schemas/", ""))
                            .validate(JSON.readTree(e.data()))
                            .forEach(
                                    m -> violations.add("SSE " + e.name() + ": " + m.getMessage()));
                }
            }
        }
        check("GET", "/api/conversations", 200, get("/api/conversations").cookie(sid));
        check("GET", "/api/conversations/{id}", 200, get("/api/conversations/" + id).cookie(sid));
        check(
                "GET",
                "/api/conversations/{id}",
                404,
                get("/api/conversations/" + UUID.randomUUID()).cookie(sid));
        fixtures.resetDocuments();
        ApiClient.Response noDocs = api.postJson(path, "{\"question\":\"thanh toán\"}");
        check("POST", "/api/conversations/{id}/messages", 409, noDocs.status(), noDocs.body());

        assertThat(violations).isEmpty();
    }
}
