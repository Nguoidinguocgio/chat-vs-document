package com.chatvsdoc.api.port.adapter;

import com.chatvsdoc.api.port.StoragePort;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lưu file trong thư mục {@code STORAGE_DIR}. Ghi qua file tạm rồi đổi tên để không bao giờ để lại
 * file ghi dở. Mã hóa khi lưu: đặt thư mục trên volume đã mã hóa (BitLocker/LUKS/đĩa cloud).
 */
public class LocalFileStorage implements StoragePort {

    // Khóa do hệ thống sinh: chỉ chữ, số, '-', '_', '.', và '/' phân cấp; cấm '..' để không thoát
    // thư mục.
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._/-]{0,254}");

    private final Path root;

    public LocalFileStorage(Path root) throws IOException {
        this.root = Files.createDirectories(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream in) throws IOException {
        Path target = resolve(key);
        // resolve() bảo đảm target nằm dưới root nên luôn có thư mục cha.
        Path dir = Objects.requireNonNull(target.getParent());
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".upload-", ".tmp");
        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                    tmp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(key);
        }
    }

    @Override
    public long size(String key) throws IOException {
        try {
            return Files.size(resolve(key));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(key);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("invalid storage key");
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return p;
    }
}
