package com.chatvsdoc.api.domain.entity;

/**
 * Kiểu enum PostgreSQL {@code message_status}; tên hằng trùng nhãn trong CSDL và giá trị trong API.
 */
public enum MessageStatus {
    streaming,
    completed,
    stopped,
    failed
}
