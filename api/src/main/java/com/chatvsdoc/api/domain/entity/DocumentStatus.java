package com.chatvsdoc.api.domain.entity;

/**
 * Kiểu enum PostgreSQL {@code document_status}; tên hằng trùng nhãn trong CSDL và giá trị trong
 * API.
 */
public enum DocumentStatus {
    processing,
    ready,
    failed
}
