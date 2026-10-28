package com.chatvsdoc.api.service.documents;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Chép luồng có giới hạn byte, đồng thời tính SHA-256; dừng đọc ngay khi vượt giới hạn. */
public final class LimitedCopy {

    /** Kết quả chép: số byte, SHA-256 (hex) và 5 byte đầu. */
    public record Result(long size, String sha256, byte[] head) {}

    /** Luồng dài hơn giới hạn. */
    public static final class TooLargeException extends IOException {
        public TooLargeException() {
            super("too large");
        }
    }

    private LimitedCopy() {}

    public static Result copy(InputStream in, OutputStream out, long maxBytes) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] head = new byte[5];
        int headLen = 0;
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new TooLargeException();
            }
            if (headLen < head.length) {
                int take = Math.min(head.length - headLen, n);
                System.arraycopy(buf, 0, head, headLen, take);
                headLen += take;
            }
            md.update(buf, 0, n);
            out.write(buf, 0, n);
        }
        byte[] actualHead = new byte[headLen];
        System.arraycopy(head, 0, actualHead, 0, headLen);
        return new Result(total, HexFormat.of().formatHex(md.digest()), actualHead);
    }
}
