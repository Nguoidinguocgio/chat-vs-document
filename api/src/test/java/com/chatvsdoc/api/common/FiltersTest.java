package com.chatvsdoc.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatvsdoc.api.support.IntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class FiltersTest {

    @Autowired MockMvc mvc;

    @Test
    void postWithoutXRequestedWithIs403() throws Exception {
        mvc.perform(post("/api/anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/anything").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postWithHeaderPassesCsrfCheck() throws Exception {
        // Đi qua filter, tới tầng MVC → 404 vì không có route (không phải 403).
        mvc.perform(post("/api/anything").header("X-Requested-With", "fetch"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIsNotAffectedByCsrfCheck() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk());
    }

    static Stream<MockHttpServletRequestBuilder> anyResponse() {
        return Stream.of(
                get("/healthz"), // 200
                get("/api/anything"), // 401
                post("/api/anything"), // 403
                get("/nope")); // 404
    }

    @ParameterizedTest
    @MethodSource("anyResponse")
    void securityHeadersOnEveryResponse(MockHttpServletRequestBuilder req) throws Exception {
        ResultActions r = mvc.perform(req);
        r.andExpect(header().string("Content-Security-Policy", SecurityHeadersFilter.CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("X-App-Version", "0.1.0"));
    }

    @Test
    void hstsOnlyOverTls() throws Exception {
        mvc.perform(get("/healthz").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
        mvc.perform(get("/healthz")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void requestIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/healthz").header("X-Request-ID", "abc-123"))
                .andExpect(header().string("X-Request-ID", "abc-123"));
        String generated =
                mvc.perform(get("/healthz").header("X-Request-ID", "bad id\n"))
                        .andReturn()
                        .getResponse()
                        .getHeader("X-Request-ID");
        assertThat(generated).matches("[0-9a-f-]{36}");
    }
}
