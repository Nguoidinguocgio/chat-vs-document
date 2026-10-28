package com.chatvsdoc.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chatvsdoc.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LoggingTest {

    private static final String MARKER = "MARKER-7f3a-NOI-DUNG-NGHIEP-VU";

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser
    void clientDisconnectIsNotLoggedAsError(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/test/client-gone"));
        assertThat(output.getAll()).doesNotContain("unhandled_exception");
    }

    @Test
    @WithMockUser
    void logsNeverContainRequestBodies(CapturedOutput output) throws Exception {
        String[] bodies = {
            "{\"question\":\"" + MARKER + "\"}", // trường lạ → 422
            "{\"" + MARKER + "\": tokenkhonghople", // JSON hỏng → 422
            MARKER // không phải JSON
        };
        for (String body : bodies) {
            mvc.perform(
                    post("/api/test/validate?q=" + MARKER)
                            .header("X-Requested-With", "fetch")
                            .header("X-Request-ID", "log-test-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body));
        }
        // Sai Content-Type (415) cũng không kéo body vào log.
        mvc.perform(
                post("/api/test/validate")
                        .header("X-Requested-With", "fetch")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(MARKER));
        assertThat(output.getAll()).doesNotContain(MARKER);
        // Vẫn có log truy cập có cấu trúc: request_id, route (không query string), status.
        assertThat(output.getAll())
                .contains("\"request_id\":\"log-test-1\"")
                .contains("\"route\":\"/api/test/validate\"")
                .contains("\"status\":422");
    }
}
