package com.chatvsdoc.api.service.retrieval;

import java.util.UUID;

/**
 * Một đoạn nguồn đưa vào ngữ cảnh trả lời. {@code n} (1..k, theo điểm giảm dần) chính là số dùng
 * làm trích dẫn {@code [n]} trong câu trả lời.
 */
public record ContextBlock(
        int n,
        UUID documentId,
        String documentName,
        int page,
        UUID chunkId,
        int charStart,
        int charEnd,
        String text,
        double score) {

    ContextBlock withN(int number) {
        return new ContextBlock(
                number, documentId, documentName, page, chunkId, charStart, charEnd, text, score);
    }
}
