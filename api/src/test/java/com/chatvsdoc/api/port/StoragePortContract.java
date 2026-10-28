package com.chatvsdoc.api.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hợp đồng mọi adapter {@link StoragePort} phải thỏa; adapter mới chỉ cần kế thừa lớp này. */
public abstract class StoragePortContract {

    protected StoragePort storage;

    protected abstract StoragePort create() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        storage = create();
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void putOpenSizeDelete() throws Exception {
        storage.put("docs/a1/original.pdf", bytes("%PDF-1.7 nội dung"));
        assertThat(storage.size("docs/a1/original.pdf"))
                .isEqualTo("%PDF-1.7 nội dung".getBytes(StandardCharsets.UTF_8).length);
        try (InputStream in = storage.open("docs/a1/original.pdf")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("%PDF-1.7 nội dung");
        }
        storage.delete("docs/a1/original.pdf");
        assertThatThrownBy(() -> storage.open("docs/a1/original.pdf"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void putOverwrites() throws Exception {
        storage.put("k", bytes("one"));
        storage.put("k", bytes("three"));
        assertThat(storage.size("k")).isEqualTo(5);
    }

    @Test
    void missingKeyBehaviour() throws Exception {
        assertThatThrownBy(() -> storage.size("missing")).isInstanceOf(FileNotFoundException.class);
        storage.delete("missing");
    }

    @Test
    void rejectsKeysEscapingTheStore() {
        for (String bad : new String[] {"../x", "a/../../x", "/etc/passwd", "C:\\x", ""}) {
            assertThatThrownBy(() -> storage.put(bad, bytes("x")))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
