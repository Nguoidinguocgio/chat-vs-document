# T09 — Backend: danh sách tài liệu, SSE sự kiện, analytics

| | |
|---|---|
| **Giai đoạn / Loại** | 1 — BB-01 Tải lên PDF / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T07 |
| **Mở khóa** | T14; phía FE: T11 |
| **Liên quan** | US-011 (AC-011.1 – 011.3); AC-003.1; PRD §6.2 (analytics); NFR-S04 |

## 1. Mục tiêu
Mọi người trong workspace thấy cùng một danh sách tài liệu và **thấy thay đổi trạng thái trong ≤ 5 giây không cần tải lại trang**; client có chỗ ghi nhận analytics mà không làm lộ nội dung.

## 2. Phạm vi
**Trong:** `GET /api/documents`, kênh SSE `GET /api/events`, bus sự kiện, `POST /api/analytics/events`, helper `emit()` cho server.
**Ngoài:** xóa tài liệu; báo cáo tuần (đọc từ bảng `events` — làm sau).

## 3. Đặc tả chi tiết

### 3.1 `GET /api/documents`
Trả `DocumentList {items, count, limit: 20}`: mọi tài liệu `deleted_at IS NULL`, sắp xếp `uploaded_at` giảm dần. `count` tính **mọi trạng thái** (khớp quy tắc giữ chỗ ở T07). Mỗi mục:
- `uploaded_by {id, display_name}` (không lộ email);
- `can_delete = (role == admin) or (uploaded_by == người gọi)` (trường có sẵn dù chưa có API xóa);
- `error_details.page_count` khi `TOO_MANY_PAGES`.
Mọi vai trò đã đăng nhập đều xem được toàn bộ danh sách (D-001).

### 3.2 Bus sự kiện và `GET /api/events` (SSE)
- Giao diện `EventBus.publish(event)` / `subscribe(user_id) -> AsyncIterator`. MVP: hiện thực **trong tiến trình**, giả định **một instance** backend (ghi rõ trong README); nếu về sau chạy nhiều instance thì thay bằng PostgreSQL `LISTEN/NOTIFY` mà không đổi giao diện.
- Vòng đệm **500 sự kiện / 10 phút** gần nhất, mỗi sự kiện có `id` tăng dần (số nguyên toàn cục).
- Kết nối: yêu cầu đăng nhập; tiêu đề `Content-Type: text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`, `Connection: keep-alive`.
- **Phát lại:** nếu có `Last-Event-ID` còn trong vòng đệm → gửi lại các sự kiện sau đó; nếu quá cũ/không có trong vòng đệm → gửi một sự kiện `resync` (client sẽ tải lại danh sách).
- Sự kiện (định dạng ở T06): `document.created`, `document.status_changed` (kèm toàn bộ `Document`), `document.deleted`, `session.revoked`, `resync`, `heartbeat` (mỗi 20 giây), thêm dòng `: ping` mỗi 10 giây để proxy không đóng kết nối.
- **Mọi người dùng** nhận các sự kiện `document.*`. `session.revoked` chỉ gửi cho người bị thu hồi (khi `revoke_user_sessions` ở T04 được gọi) rồi đóng kết nối.
- Đóng kết nối khi client ngắt (hủy tác vụ, không rò rỉ); giới hạn tối đa **3 kết nối đồng thời mỗi người dùng** (kết nối cũ nhất bị đóng).
- Kết nối bị từ chối khi phiên hết hạn (`401`) hoặc đang yêu cầu đổi mật khẩu.

### 3.3 `POST /api/analytics/events`
Body `{events: [{name, ts, props}]}`, tối đa 50 sự kiện/yêu cầu; trả `204`.
- **Danh sách trắng tên** cho phép từ client: `chat_answer_first_token` (EV-021), `chat_answer_stopped` (EV-022), `chat_answer_regenerated` (EV-025), `citation_clicked` (EV-030), `client_network_lost`/`client_network_restored` (EV-050). Tên khác → loại bỏ cả lô với `422 VALIDATION_ERROR`.
- **Danh sách trắng thuộc tính theo từng sự kiện** (ví dụ EV-021: `question_id`, `ttft_ms`). Giá trị chỉ được là số, boolean hoặc chuỗi ≤ 64 ký tự khớp mẫu ID/enum; vi phạm → `422`. Tuyệt đối không chấp nhận trường văn bản tự do.
- Máy chủ tự gắn `user_id`, `session_id` (từ phiên), `role`, `app_version`, `ts` máy chủ; lưu vào `events`. Timestamp từ client chỉ lưu thêm làm `props.client_ts`, tối đa lệch ±24 giờ.

### 3.4 Helper phía máy chủ
`emit(name, user_id, **props)` ghi vào `events` bằng cùng bộ lọc danh sách trắng (bảng tên/thuộc tính nằm một chỗ: `services/analytics_schema.py`, gồm cả EV-001/002/010/011/012/013/020/023/024). T07, T08, T14 dùng chung.

## 4. Tệp sẽ tạo
`routers/documents.py` (phần đọc), `routers/events.py`, `routers/analytics.py`, `services/events.py`, `services/analytics.py`, `services/analytics_schema.py`, `tests/test_events_*.py`.

## 5. Tiêu chí hoàn thành
- [ ] AC-011.1: `GET /documents` trả đủ tên, số trang, người tải, thời điểm, trạng thái, lý do lỗi.
- [ ] AC-011.2: người A tải lên → người B (kết nối SSE riêng) nhận `document.created` rồi `document.status_changed` trong ≤ 5 giây (test với hai client).
- [ ] AC-011.3 (phần backend): `count` và `limit` đúng, tính cả tài liệu `failed`.
- [ ] Ngắt SSE rồi kết nối lại với `Last-Event-ID` → nhận đủ sự kiện bị lỡ, không trùng; với ID quá cũ → nhận `resync`.
- [ ] `heartbeat` đến mỗi 20 giây (đồng hồ giả); kết nối không bị đóng bởi proxy giả lập có timeout 30 giây.
- [ ] Hủy kết nối của client không để lại tác vụ nền (kiểm tra số subscriber trở về 0).
- [ ] `session.revoked` chỉ đến đúng người bị thu hồi; kết nối đóng sau đó (AC-003.1).
- [ ] Người dùng mở thêm kết nối thứ 4 → kết nối cũ nhất bị đóng.
- [ ] Analytics: tên/thuộc tính ngoài danh sách trắng bị `422`; chuỗi dài hoặc có khoảng trắng/nội dung tự do bị từ chối; người gửi không thể giả `user_id`.
- [ ] `emit()` từ T07/T08 tạo bản ghi `events` đúng EV-010/011/012/013 (không chứa tên file hay văn bản).
- [ ] Người chưa đăng nhập: cả ba endpoint trả `401`.

## 6. Kiểm thử
pytest với client SSE (httpx stream); đồng hồ giả cho heartbeat; kiểm thử đồng thời hai người dùng; kiểm thử rò rỉ tác vụ khi ngắt kết nối.

## 7. Ghi chú
Nếu CNTT xác nhận proxy đệm SSE (OQ-UX-02), đây là nơi thêm phương án dự phòng (ví dụ cho phép client chuyển sang thăm dò `GET /documents` mỗi 5 giây khi `EventSource` lỗi liên tục). Quyết định cùng T11.
