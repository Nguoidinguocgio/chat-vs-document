package com.chatvsdoc.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Đọc tên file từ Content-Disposition (không qua Paths.get — lỗi 500 trên Windows trước đây). */
class DocumentControllerTest {

    @Test
    void quotedFilenameWithCharactersIllegalInWindowsPaths() {
        assertThat(
                        DocumentController.fileName(
                                "form-data; name=\"file\"; filename=\"Báo cáo: Q3? *final*.pdf\""))
                .isEqualTo("Báo cáo: Q3? *final*.pdf");
        assertThat(
                        DocumentController.fileName(
                                "form-data; name=\"file\"; filename=\"a \\\"b\\\".pdf\""))
                .isEqualTo("a \"b\".pdf");
    }

    @Test
    void rfc5987FilenameTakesPrecedence() {
        assertThat(
                        DocumentController.fileName(
                                "form-data; name=\"file\"; filename=\"fallback.pdf\"; filename*=UTF-8''H%E1%BB%A3p%20%C4%91%E1%BB%93ng+1.pdf"))
                .isEqualTo("Hợp đồng+1.pdf");
    }

    @Test
    void missingOrUnquoted() {
        assertThat(DocumentController.fileName(null)).isEmpty();
        assertThat(DocumentController.fileName("form-data; name=\"file\"")).isEmpty();
        assertThat(DocumentController.fileName("form-data; name=file; filename=plain.pdf"))
                .isEqualTo("plain.pdf");
    }
}
