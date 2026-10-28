# T19 — Frontend: Dừng, Tạo lại, lỗi, mất kết nối

| | |
|---|---|
| **Giai đoạn / Loại** | 3 — Kiểm chứng và độ bền / Frontend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T16; dùng MSW nếu T17 chưa xong |
| **Mở khóa** | T21, T22 |
| **Liên quan** | US-026; US-020 (AC-020.2); PRD §5.2 (mạng và hệ thống); SDD ST-02, ST-04, CMP-02, CMP-05; EV-022, EV-025, EV-050 |

## 1. Mục tiêu
Mọi tình huống xấu đều có **lối thoát rõ ràng**: người dùng dừng được câu trả lời, tạo lại được, biết chính xác chuyện gì xảy ra, và **không bao giờ** thấy một câu trả lời dở dang trông như hoàn chỉnh hay một màn hình treo.

## 2. Phạm vi
**Trong:** nút Gửi ↔ Dừng, Tạo lại/Thử lại, bốn trạng thái ST-02 còn lại (`stopped`, `interrupted`, `timeout`, `failed`), banner mất kết nối, trạng thái Composer khi offline, xử lý hết phiên giữa luồng, analytics.
**Ngoài:** thử lại tự động câu trả lời (luôn do người dùng chọn — tránh tốn chi phí LLM).

## 3. Đặc tả chi tiết

### 3.1 Dừng (US-026)
- Khi `isAnswering`, nút Gửi chuyển thành nút **Dừng** (■, `aria-label="Dừng tạo câu trả lời"`).
- Bấm: (1) chuyển thẻ trả lời sang `stopped` **ngay lập tức** (lạc quan); (2) `AbortController.abort()` đóng luồng; (3) gọi `POST /api/messages/{id}/stop` (nếu đã có `message_id` từ `meta`; nếu chưa, chỉ hủy luồng); (4) ghi EV-022 `chat_answer_stopped {question_id, elapsed_ms}`. Tổng thời gian đến khi UI dừng **≤ 1 giây**.
- Kết quả: giữ nguyên phần đã hiển thị + nhãn "Đã dừng" + nút "↻ Tạo lại"; **không** hiện SourceList/👍👎; thông báo qua `aria-live` "Đã dừng". Composer mở lại.

### 3.2 Tạo lại / Thử lại
Nút trong thẻ (`↻ Tạo lại` cho `stopped`/`interrupted`; `↻ Thử lại` cho `timeout`/`failed`) → `POST /api/messages/{id}/regenerate` (SSE cùng định dạng, **cùng `message_id`**): xóa nội dung cũ của thẻ, quay về `searching`, dùng lại `useAnswerStream` (T16). Nếu thẻ chưa có `message_id` (lỗi xảy ra trước `meta`) → gửi lại câu hỏi bằng đường thường. `409 NOT_REGENERABLE` → làm mới trạng thái từ `GET /conversations/{id}`. Ghi EV-025 `chat_answer_regenerated {question_id, previous_outcome}`. Chỉ một thao tác tại một thời điểm (vô hiệu hóa nút khi đang chạy).

### 3.3 Bảng trạng thái (PRD §5.2)
| Trạng thái | Phát hiện | Trình bày | Hành động |
|---|---|---|---|
| `stopped` | người dùng bấm Dừng | phần đã có + "Đã dừng" | Tạo lại |
| `interrupted` | luồng đóng không có `done`/`error`; hoặc mất mạng khi đang nhận | phần đã có + ⚠ `chat.interrupted`, viền cảnh báo (`--color-warning`) | Tạo lại (khi online) |
| `timeout` | 30 giây không có sự kiện (client) hoặc `error{timeout}` | ⚠ `chat.timeout` | Thử lại |
| `failed` | `error{llm_error}`, `error{rate_limited}` hoặc lỗi HTTP 5xx | ⚠ `chat.llm_unavailable` (không hiển thị lỗi kỹ thuật thô) | Thử lại |
Bất biến (CN-03): chỉ `answered`/`partial` có SourceList và được trình bày như hoàn chỉnh; không trạng thái nào ở bảng trên giống `answered`.

### 3.4 Mất kết nối
- `ConnectionBanner` (T05) hiển thị khi `offline`: `network.offline`; khi có lại mạng hiển thị "Đã kết nối lại" trong 3 giây rồi ẩn. Ghi EV-050 `client_network_lost`/`client_network_restored {context: 'upload'|'streaming'|'idle', duration_ms}`.
- Đang nhận câu trả lời mà mất mạng → `interrupted` (3.3). Đang soạn → giữ nguyên nội dung; Gửi vô hiệu hóa (ST-04 `offline`). Có mạng lại → Composer mở ngay; **không** tự gửi lại.
- Tải lên (T10) và SSE workspace (T11) có cơ chế riêng; `ConnectionBanner` chỉ phản ánh tình trạng chung.

### 3.5 Các lỗi khác khi gửi
- `401 SESSION_EXPIRED` giữa luồng → xử lý toàn cục (T05): về `/login`, **nháp được giữ** (nội dung câu hỏi vừa gửi cũng được lưu lại thành nháp trước khi chuyển để người dùng không mất).
- `409 NO_READY_DOCUMENTS` → làm mới tài liệu; Composer tự chuyển sang trạng thái khóa tương ứng; thẻ vừa thêm bị gỡ.
- `422 QUESTION_INVALID` → thông báo tại ô nhập, thẻ vừa thêm bị gỡ.
- Tài liệu bị xóa khi đang trả lời: câu trả lời đang tạo hoàn tất bình thường; trích dẫn tới tài liệu đó hiển thị "đã xóa" (đã có ở T16).

### 3.6 Tiếp cận
Mọi thay đổi trạng thái được thông báo qua vùng `aria-live` riêng ("Đã dừng", "Kết nối bị gián đoạn", "Hệ thống phản hồi chậm…"); lỗi dùng `role="alert"`; nút có nhãn rõ; thứ tự focus không bị mất khi thẻ thay đổi (giữ focus ở nút vừa bấm hoặc chuyển tới nút "Tạo lại").

## 4. Tệp sẽ tạo
Mở rộng `features/chat/{useAnswerStream, answerReducer, AnswerCard, Composer}`, `features/chat/StatusNotes.tsx`, `app/ConnectionBanner.tsx` (bản đầy đủ), `shared/lib/network.ts`, mở rộng `mocks` (kịch bản `interrupted`, `timeout`, `llm_error`, 409/401 giữa luồng), `*.test.ts`. (Các thành phần trong danh sách trên là tệp `.vue`.)

## 5. Tiêu chí hoàn thành
- [ ] AC-020.3 + US-026: khi đang trả lời, Gửi bị thay bằng Dừng; Dừng làm UI dừng ≤ 1 giây (đồng hồ giả), giữ phần đã hiển thị, nhãn "Đã dừng", có "Tạo lại"; `POST …/stop` được gọi đúng một lần với đúng `message_id`.
- [ ] Tạo lại từ `stopped`: thẻ về `searching` rồi hoàn tất bình thường; từ `interrupted`/`timeout`/`failed` tương tự; nút bị vô hiệu hóa trong lúc chạy.
- [ ] Ngắt luồng giữa chừng (MSW đóng đột ngột) → `interrupted` với viền cảnh báo, không có SourceList; không bao giờ trông như hoàn chỉnh.
- [ ] 30 giây không có sự kiện (đồng hồ giả) → `timeout` đúng thông điệp "Hệ thống phản hồi chậm hơn bình thường. Thử lại?"; thử lại hoạt động.
- [ ] `error{llm_error}`/`error{rate_limited}`/5xx → `chat.llm_unavailable` (không lộ thông báo kỹ thuật).
- [ ] AC-020.2: mọi kịch bản MSW (`answered`, `not_found`, `partial`, `interrupted`, `timeout`, `llm_error`) kết thúc ở một trạng thái có lối thoát (kết quả, thông báo, hoặc nút thử lại) — không trạng thái nào treo.
- [ ] Mất mạng: banner xuất hiện/ẩn đúng; đang nhận → `interrupted`; đang soạn → giữ nội dung, Gửi khóa; có mạng → mở lại, không tự gửi.
- [ ] `SESSION_EXPIRED` giữa luồng → về đăng nhập; đăng nhập lại → câu hỏi vừa gửi có trong ô nhập.
- [ ] `NO_READY_DOCUMENTS` và `QUESTION_INVALID` xử lý như 3.5.
- [ ] EV-022, EV-025, EV-050 được gửi đúng thuộc tính, không chứa nội dung.
- [ ] Thông báo `aria-live`/`role="alert"` đúng; axe-core 0 lỗi serious/critical; khớp prototype.

## 6. Kiểm thử
Vitest (reducer đủ chuyển trạng thái; đồng hồ giả cho 30 giây và ≤ 1 giây); MSW cho từng kịch bản; Playwright (T21) mô phỏng offline bằng `context.setOffline()`.

## 7. Ghi chú
Không tự động gửi lại để tránh tốn chi phí LLM và tránh trả lời trùng; người dùng luôn chủ động (khớp ALG-03).
