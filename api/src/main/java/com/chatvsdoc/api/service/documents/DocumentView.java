package com.chatvsdoc.api.service.documents;

import com.chatvsdoc.api.domain.entity.DocumentStatus;
import com.chatvsdoc.api.service.auth.AuthUser;
import com.chatvsdoc.api.service.events.EventBus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code Document} trong hợp đồng API (T06 mục 3.1). */
public record DocumentView(
        UUID id,
        String name,
        Integer pageCount,
        DocumentStatus status,
        String errorCode,
        Map<String, Object> errorDetails,
        List<Integer> unreadablePages,
        Uploader uploadedBy,
        Instant uploadedAt,
        boolean canDelete) {

    /** Người tải lên — không lộ email. */
    public record Uploader(UUID id, String displayName) {}

    /** {@code can_delete} = admin hoặc chính người tải lên. */
    public DocumentView forViewer(AuthUser viewer) {
        boolean can = viewer.isAdmin() || viewer.id().equals(uploadedBy.id());
        return new DocumentView(
                id,
                name,
                pageCount,
                status,
                errorCode,
                errorDetails,
                unreadablePages,
                uploadedBy,
                uploadedAt,
                can);
    }

    /** Dữ liệu sự kiện SSE: mỗi người nhận có {@code can_delete} của riêng mình. */
    public EventBus.ViewerScoped asEvent() {
        return this::forViewer;
    }
}
