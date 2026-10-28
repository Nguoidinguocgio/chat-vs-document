package com.chatvsdoc.api.service.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

/** AC-012.2: dừng đọc ngay khi vượt giới hạn — không đọc hết một luồng 50 MB. */
class LimitedCopyTest {

    /** Luồng vô hạn đếm số byte đã bị đọc. */
    static final class CountingStream extends InputStream {
        long read;

        @Override
        public int read() {
            read++;
            return 'x';
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int n = Math.min(len, 8192);
            read += n;
            return n;
        }
    }

    @Test
    void stopsReadingRightAfterLimit() {
        long limit = 10L * 1024 * 1024;
        CountingStream in = new CountingStream();
        assertThatThrownBy(() -> LimitedCopy.copy(in, OutputStream.nullOutputStream(), limit))
                .isInstanceOf(LimitedCopy.TooLargeException.class);
        assertThat(in.read).isGreaterThan(limit).isLessThan(limit + 64 * 1024 + 1);
    }

    @Test
    void copiesExactlyAtLimitAndComputesSha256AndHead() throws Exception {
        byte[] data = "%PDF-1.7 abc".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LimitedCopy.Result r = LimitedCopy.copy(new ByteArrayInputStream(data), out, data.length);
        assertThat(r.size()).isEqualTo(data.length);
        assertThat(out.toByteArray()).isEqualTo(data);
        assertThat(new String(r.head())).isEqualTo("%PDF-");
        assertThat(r.sha256())
                .hasSize(64)
                .isEqualTo(
                        LimitedCopy.copy(
                                        new ByteArrayInputStream(data),
                                        OutputStream.nullOutputStream(),
                                        100)
                                .sha256());
    }

    @Test
    void sanitizeNameKeepsVietnameseAndStripsPathsAndControls() {
        assertThat(DocumentService.sanitizeName("Hợp đồng A (bản cuối).pdf"))
                .isEqualTo("Hợp đồng A (bản cuối).pdf");
        assertThat(DocumentService.sanitizeName("../../x.pdf")).isEqualTo("x.pdf");
        assertThat(DocumentService.sanitizeName("C:\\Users\\a\\b.pdf")).isEqualTo("b.pdf");
        assertThat(DocumentService.sanitizeName("a\u0000b\nc.pdf")).isEqualTo("abc.pdf");
        assertThat(DocumentService.sanitizeName("x".repeat(300) + ".pdf")).hasSize(255);
    }
}
