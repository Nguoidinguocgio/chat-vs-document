package com.chatvsdoc.api.port;

import java.util.List;

/**
 * Sinh vector embedding đa ngôn ngữ, chuẩn hóa độ dài 1 (dùng khoảng cách cosine). Mô hình cần tiền
 * tố riêng cho văn bản/câu hỏi thì đóng gói trong {@link #embedDocuments} / {@link #embedQuery}.
 */
public interface EmbeddingPort {

    /** Số chiều của mọi vector trả về — phải bằng {@code EMBEDDING_DIM} của cột {@code chunks}. */
    int dimension();

    /** Một vector cho mỗi văn bản, cùng thứ tự đầu vào. */
    List<float[]> embed(List<String> texts);

    /** Vector cho các đoạn tài liệu (T08). */
    default List<float[]> embedDocuments(List<String> texts) {
        return embed(texts);
    }

    /** Vector cho câu hỏi (T12). */
    default float[] embedQuery(String query) {
        return embed(List.of(query)).get(0);
    }

    /** Lỗi gọi nhà cung cấp; {@code transient} = nên thử lại (mạng, 5xx, quá tải). */
    class EmbeddingException extends RuntimeException {
        private final boolean transientError;

        public EmbeddingException(String message, boolean transientError, Throwable cause) {
            super(message, cause);
            this.transientError = transientError;
        }

        public boolean isTransient() {
            return transientError;
        }
    }
}
