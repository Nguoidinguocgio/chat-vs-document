package com.chatvsdoc.api.service.events;

import com.chatvsdoc.api.service.auth.AuthUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bus sự kiện cho kênh {@code /api/events}. Bản MVP chạy trong tiến trình (một instance backend);
 * chạy nhiều instance thì thay bằng PostgreSQL LISTEN/NOTIFY mà không đổi giao diện này.
 */
public interface EventBus {

    /** Sự kiện đã đánh số; {@code targetUserId == null} nghĩa là gửi mọi người. */
    record BusEvent(long id, Instant ts, String type, Object data, UUID targetUserId) {

        public boolean visibleTo(UUID userId) {
            return targetUserId == null || targetUserId.equals(userId);
        }

        /** Dữ liệu cho người nhận cụ thể (ví dụ {@code can_delete} phụ thuộc người xem). */
        public Object dataFor(AuthUser viewer) {
            return data instanceof ViewerScoped v ? v.forViewer(viewer) : data;
        }
    }

    /** Dữ liệu sự kiện khác nhau theo người nhận. */
    interface ViewerScoped {
        Object forViewer(AuthUser viewer);
    }

    /** Đăng ký nhận sự kiện. {@code replay == null} nghĩa là client phải {@code resync}. */
    record Subscription(List<BusEvent> replay, Runnable cancel) {}

    void publish(String type, Object data);

    void publishTo(UUID userId, String type, Object data);

    /**
     * Đăng ký listener và lấy (nguyên tử) các sự kiện sau {@code lastEventId} còn trong vòng đệm.
     * {@code lastEventId == null}: kết nối mới, không phát lại.
     */
    Subscription subscribe(UUID userId, Long lastEventId, Consumer<BusEvent> listener);

    int subscriberCount();
}
