package com.chatvsdoc.api.service.documents;

import java.util.UUID;

/**
 * Hàng đợi xử lý tài liệu. Bảng {@code documents} là nguồn sự thật; hiện thực trong tiến trình có
 * thể thay bằng hàng đợi ngoài mà không sửa nghiệp vụ.
 */
public interface JobQueue {

    /** Xếp hàng xử lý; bỏ qua nếu tài liệu đang chờ/đang chạy. */
    void enqueue(UUID documentId);
}
