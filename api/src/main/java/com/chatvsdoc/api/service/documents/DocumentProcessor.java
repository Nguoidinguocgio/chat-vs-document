package com.chatvsdoc.api.service.documents;

import java.util.UUID;

/** Xử lý một tài liệu đã tải lên (hiện thực: {@code PdfPipeline}, T08). */
public interface DocumentProcessor {

    void processDocument(UUID documentId);
}
