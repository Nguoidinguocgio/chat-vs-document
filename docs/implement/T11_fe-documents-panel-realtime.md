# T11 — Frontend: bảng Tài liệu và đồng bộ thời gian thực

| | |
|---|---|
| **Giai đoạn / Loại** | 1 — BB-01 Tải lên PDF / Frontend |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T05, T06 (dùng MSW); tích hợp thật với T09 |
| **Mở khóa** | T15 (ô nhập cần tóm tắt trạng thái tài liệu), T18 (dùng chung khe bảng phải) |
| **Liên quan** | US-011 (AC-011.1 – 011.3), US-012 (AC-012.3 – 012.8); SDD CMP-01, CMP-09, CMP-10, CMP-11, ST-01, ALG-03, 3.4 (trạng thái tài liệu); prototype SCR-03 |

## 1. Mục tiêu
Người dùng luôn thấy chính xác tài liệu nào đang được dùng để trả lời, tài liệu nào đang xử lý, tài liệu nào lỗi **và vì sao** — cập nhật ngay khi có thay đổi, kể cả do người khác tải lên.

## 2. Phạm vi
**Trong:** `shared/realtime` (SSE), `RightPanel` (khe một bảng), `DocumentsPanel`, `DocumentRow`, bộ đếm trên thanh trên, tóm tắt trạng thái tài liệu, trạng thái trống của khu chat, thử lại xử lý.
**Ngoài:** xóa/tóm tắt tài liệu (menu "⋯" chưa hiển thị trong MVP lõi, trừ "Thử lại" cho lỗi hệ thống); xem nguồn (T18 thêm vào cùng khe).

## 3. Đặc tả chi tiết

### 3.1 `shared/realtime`
- `connectWorkspaceEvents()` mở `EventSource('/api/events')` (cookie cùng origin). Chỉ module này được dùng `EventSource` (lint R-3).
- **Một kết nối cho cả các tab:** dùng `navigator.locks.request('cvd-sse-leader', …)` để một tab giữ kết nối; tab giữ kết nối phát lại mọi sự kiện cho các tab khác qua `BroadcastChannel('cvd-events')`. Nếu `navigator.locks` không có → mỗi tab tự kết nối (chấp nhận).
- **Kết nối lại (ALG-03):** khi `onerror` đóng hẳn → chờ `1 s, 2 s, 4 s, … tối đa 30 s` cộng ngẫu nhiên ±20% rồi tạo `EventSource` mới (trình duyệt tự gửi `Last-Event-ID`). Khi kết nối được lại sau khi từng lỗi → tải lại `GET /api/documents` (phòng sự kiện bị lỡ).
- Sự kiện → cập nhật cache TanStack Query (`['documents']`): `document.created` thêm; `document.status_changed` thay thế theo `id`; `document.deleted` gỡ; `resync` → invalidate; `session.revoked` → xóa cache **và bản nháp**, chuyển `/login?reason=revoked` (trang đăng nhập hiển thị `auth.disabled`); `heartbeat` bỏ qua.
- Phương án dự phòng (nếu OQ-UX-02 xác nhận proxy đệm SSE): bật chế độ thăm dò `GET /api/documents` mỗi 5 giây khi SSE lỗi liên tục ≥ 3 lần; tắt khi SSE phục hồi. Đặt sau cờ cấu hình `VITE_POLL_FALLBACK`.

### 3.2 `RightPanel` (khe duy nhất, CMP-10)
Trạng thái `panel: 'closed' | 'documents' | 'source'` (T18 dùng `source`). Mặc định ≥ 1280 px mở `documents` khi workspace còn trống hoặc lần đầu; < 1280 px là ngăn kéo phủ có lớp mờ, bẫy focus, Esc đóng. Nút ✕ đóng; bộ đếm trên thanh trên bật/tắt bảng.

### 3.3 `DocumentsPanel` và `DocumentRow` (CMP-11)
- Tiêu đề "Tài liệu" + huy hiệu `x/20` (đổi sang màu cảnh báo từ **18/20**); `UploadDropzone` (T10) ở đầu; `RejectionList` (T10); danh sách `GET /api/documents` cộng các `UploadTask` đang chạy (T10) ở **cùng danh sách**: task hiển thị ở đầu với trạng thái "Đang tải lên".
- `DocumentRow`: icon 📄, tên (rút gọn giữa, `title` = tên đầy đủ), `"{n} trang · {người tải} · {thời điểm}"` (thời điểm tương đối trong ngày: "09:02 hôm nay", "Hôm qua", `dd/MM/yyyy`), trạng thái theo bảng — luôn **icon + chữ**:

| Điều kiện | Hiển thị |
|---|---|
| UploadTask `uploading` | `⬆ Đang tải lên {p}%` + thanh tiến trình |
| UploadTask `interrupted` | `⚠ Gián đoạn` + "Thử lại" / "Hủy" |
| `processing` | `⏳ Đang xử lý` (spinner; tĩnh nếu reduced-motion) |
| `ready`, không trang lỗi | `✓ Sẵn sàng` |
| `ready`, `unreadable_pages` không rỗng | `✓ Sẵn sàng` + `ⓘ documents.partial_unreadable` ("{k} trang không đọc được văn bản: trang a, b") |
| `failed` | `✕ Lỗi` + `ⓘ` lý do theo `error_code` → khóa `documents.too_many_pages` (kèm `{n}` từ `error_details.page_count`), `password_protected`, `scanned`, `corrupted`, `system_error` |
| `failed` + `SYSTEM_ERROR` | thêm nút "↻ Thử lại" → `POST /api/documents/{id}/retry`, chuyển hàng về `processing` ngay (lạc quan) |

- Danh sách có `aria-label="Danh sách tài liệu"`; thay đổi trạng thái thông báo qua vùng `aria-live` ("Tài liệu {tên} đã sẵn sàng" / "… bị lỗi").
- Toast `documents.first_ready_toast` ("Đã sẵn sàng. Hãy đặt câu hỏi.") khi tài liệu **đầu tiên** của workspace chuyển `ready` (so sánh số tài liệu `ready` trước và sau).

### 3.4 Tóm tắt trạng thái tài liệu cho ô nhập (ST-04, phần liên quan tài liệu)
Composable `useDocumentsSummary()` trả `{total, ready, processing, failed, hasReady}` (tính cả `UploadTask` đang tải vào `processing`). T15 dùng composable này để quyết định `no_documents | processing_only | all_failed`.

### 3.5 Trạng thái trống của khu chat (CMP-09, phần tài liệu)
`EmptyWorkspaceState` hiển thị theo `useDocumentsSummary()` (đúng ba biến thể prototype): chưa có tài liệu (lời dẫn + nút "Tải lên" + giới hạn), chỉ có đang xử lý (`chat.all_processing`), tất cả lỗi (`chat.all_failed` + nút mở bảng Tài liệu). Component này được T15 đặt vào `MessageList` khi chưa có tin nhắn.

## 4. Tệp sẽ tạo
`shared/realtime/{workspaceEvents.ts, leader.ts}`, `app/RightPanel.vue` + store (Pinia), `features/documents/{DocumentsPanel.vue,DocumentRow.vue,DocumentStatus.vue,EmptyWorkspaceState.vue,useDocuments.ts,useDocumentsSummary.ts,api.ts}`, `*.test.ts`.

## 5. Tiêu chí hoàn thành
- [ ] AC-011.1: danh sách hiển thị đủ tên, số trang, người tải, thời điểm, trạng thái; file lỗi kèm lý do đúng văn bản AC-012.x (kiểm tra từng `error_code`).
- [ ] AC-011.2: hai trình duyệt/tab — A tải lên, B thấy thêm tài liệu và đổi trạng thái trong ≤ 5 giây không tải lại trang (E2E với backend thật hoặc SSE giả).
- [ ] AC-011.3: bộ đếm "x/20" đúng và đổi màu cảnh báo từ 18/20.
- [ ] AC-012.8: tài liệu `ready` có `unreadable_pages=[7,8]` hiển thị "Sẵn sàng" kèm "2 trang không đọc được văn bản: trang 7, 8".
- [ ] AC-012.3: lỗi `TOO_MANY_PAGES` hiển thị "File có {n} trang, vượt giới hạn 100 trang." với `n` thật.
- [ ] AC-012.7: `SYSTEM_ERROR` có nút "Thử lại"; bấm → hàng về "Đang xử lý" ngay, sau đó theo SSE.
- [ ] Ngắt SSE (MSW) → tự kết nối lại theo 1 s/2 s/4 s…; kết nối lại → danh sách được tải lại; `resync` → danh sách được tải lại.
- [ ] Nhiều tab: chỉ **một** `EventSource` hoạt động; các tab còn lại vẫn nhận cập nhật (test với hai trang trong Playwright).
- [ ] `session.revoked` → về `/login` ≤ 60 giây (AC-003.1) và cache + nháp bị xóa.
- [ ] Trạng thái trống đúng ba biến thể; thông báo toast chỉ hiện đúng một lần cho tài liệu đầu tiên.
- [ ] Mọi trạng thái đều có icon + chữ (không chỉ màu); axe-core 0 lỗi serious/critical; dùng được bằng bàn phím; khớp prototype ở 1440 px, ngăn kéo ở 1100 px.
- [ ] Lint R-3: chỉ `shared/realtime` dùng `EventSource`.

## 6. Kiểm thử
Vitest + MSW (SSE giả, ngắt/đóng, `resync`); Vue Test Utils/Testing Library cho từng biến thể `DocumentRow`; Playwright hai ngữ cảnh trình duyệt cho đồng bộ (cùng T21).

## 7. Ghi chú
Thời điểm "hôm nay/hôm qua" tính theo múi giờ `Asia/Ho_Chi_Minh`. Dữ liệu `uploaded_at` từ máy chủ là UTC.

**Ghi chú khi hiện thực (2026-10-28):**
- Kênh SSE `/api/events` ở `shared/realtime/workspaceEvents.ts`: chỉ một tab giữ kết nối (Web Locks `cvd-sse-leader`) và chia sự kiện qua `BroadcastChannel`; kết nối lại 1 s·2ⁿ (tối đa 30 s, ±20 %), sau khi nối lại phát `resync` để làm mới danh sách. `VITE_POLL_FALLBACK=true` bật thăm dò dự phòng (OQ-UX-02).
- `session.revoked` → đăng xuất về `/login?reason=revoked`; thêm cờ `leaving` trong `app/session.ts` để lỗi 401 của các truy vấn khác không ghi đè lý do chuyển trang.
- Bảng Tài liệu mở sẵn lần đầu ở màn hình ≥ 1280 px; toast "đã sẵn sàng" chỉ cho tài liệu ready ĐẦU TIÊN của workspace.
