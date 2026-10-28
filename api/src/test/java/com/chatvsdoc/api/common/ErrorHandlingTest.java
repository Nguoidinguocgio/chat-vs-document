package com.chatvsdoc.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatvsdoc.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Mọi loại lỗi đều theo định dạng chuẩn (endpoint gây lỗi: support/ErrorTestController). */
@IntegrationTest
class ErrorHandlingTest {

    @Autowired MockMvc mvc;

    private static ResultActions expectError(ResultActions r, int status, String code, String key)
            throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message_key").value(key))
                .andExpect(jsonPath("$.error.details").isMap());
    }

    @Test
    @WithMockUser
    void appExceptionUsesItsCodeStatusAndDetails() throws Exception {
        expectError(
                        mvc.perform(get("/api/test/app-error")),
                        409,
                        "DOCUMENT_LIMIT_REACHED",
                        "documents.limit_reached")
                .andExpect(jsonPath("$.error.details.limit").value(20));
    }

    @Test
    @WithMockUser
    void unexpectedExceptionIsGeneric500() throws Exception {
        expectError(mvc.perform(get("/api/test/boom")), 500, "INTERNAL_ERROR", "errors.generic");
    }

    @Test
    @WithMockUser
    void beanValidationIs422WithSnakeCaseFields() throws Exception {
        expectError(
                        mvc.perform(
                                post("/api/test/validate")
                                        .header("X-Requested-With", "fetch")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"display_name\":\"\",\"code\":\"toolong\"}")),
                        422,
                        "VALIDATION_ERROR",
                        "errors.validation")
                .andExpect(jsonPath("$.error.details.fields.display_name").value("NotBlank"))
                .andExpect(jsonPath("$.error.details.fields.code").value("Size"));
    }

    @Test
    @WithMockUser
    void malformedJsonIs422() throws Exception {
        expectError(
                mvc.perform(
                        post("/api/test/validate")
                                .header("X-Requested-With", "fetch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json")),
                422,
                "VALIDATION_ERROR",
                "errors.validation");
    }

    @Test
    @WithMockUser
    void unknownRouteIs404() throws Exception {
        expectError(mvc.perform(get("/api/nope")), 404, "NOT_FOUND", "errors.not_found");
    }

    @Test
    void unauthenticatedApiIs401() throws Exception {
        expectError(
                mvc.perform(get("/api/test/app-error")),
                401,
                "UNAUTHENTICATED",
                "errors.unauthenticated");
    }

    @Test
    @WithMockUser
    void accessDeniedIs403() throws Exception {
        expectError(mvc.perform(get("/api/test/forbidden")), 403, "FORBIDDEN", "errors.forbidden");
    }

    @Test
    void healthzIsPublic() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
