package com.chatvsdoc.api.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lưu trữ file gốc. Khóa do hệ thống sinh (không lấy từ tên file người dùng). Mã hóa khi lưu do
 * tầng đĩa/volume hoặc dịch vụ lưu trữ đảm nhiệm (AC-060.1), không mã hóa ở tầng ứng dụng.
 */
public interface StoragePort {

    /** Ghi toàn bộ {@code in} vào {@code key}; ghi đè nếu đã tồn tại. Không đóng {@code in}. */
    void put(String key, InputStream in) throws IOException;

    /**
     * Mở luồng đọc; người gọi phải đóng. Ném {@link java.io.FileNotFoundException} nếu không có.
     */
    InputStream open(String key) throws IOException;

    /** Kích thước tính bằng byte. Ném {@link java.io.FileNotFoundException} nếu không có. */
    long size(String key) throws IOException;

    /** Xóa; không lỗi nếu khóa không tồn tại. */
    void delete(String key) throws IOException;
}
