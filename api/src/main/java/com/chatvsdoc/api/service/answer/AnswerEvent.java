package com.chatvsdoc.api.service.answer;

import com.chatvsdoc.api.domain.entity.AnswerType;
import com.chatvsdoc.api.service.retrieval.ContextBlock;

/**
 * Sự kiện phát ra khi sinh câu trả lời (T13 mục 3.1); luồng luôn kết thúc bằng đúng một
 * Done/Failed.
 */
public sealed interface AnswerEvent {

    /** Một mảnh văn bản (đã lọc trích dẫn không hợp lệ và các dấu hiệu điều khiển). */
    record Delta(String text) implements AnswerEvent {}

    /** Lần đầu {@code [index]} xuất hiện. */
    record Citation(int index, ContextBlock block) implements AnswerEvent {}

    /** Kết thúc bình thường, kèm số đo để lưu và phát EV-023. */
    record Done(
            AnswerType answerType,
            String missing,
            boolean uncited,
            Integer ttftMs,
            int totalMs,
            Integer inputTokens,
            Integer outputTokens)
            implements AnswerEvent {}

    /** {@code timeout | rate_limited | llm_error}. */
    record Failed(String code) implements AnswerEvent {}
}
