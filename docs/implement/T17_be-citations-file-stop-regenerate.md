# T17 — Backend: trích dẫn, tải file PDF, dừng, tạo lại

| | |
|---|---|
| **Giai đoạn / Loại** | 3 — Kiểm chứng và độ bền / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T14 |
| **Mở khóa** | T18, T19 (tích hợp thật) |
| **Liên quan** | US-030 (AC-030.1 – 030.3), US-026; D-001; EV-022, EV-025; hợp đồng T06 |

## 1. Mục tiêu
Cho phép người dùng **kiểm chứng** câu trả lời (xem đúng trang PDF và đoạn đã dùng), **dừng** một câu trả lời đang tạo, và **tạo lại** khi bị dừng/gián đoạn/lỗi.

## 2. Phạm vi
**Trong:** `GET /api/citations/{message_id}/{index}`, `GET /api/documents/{id}/file` (hỗ trợ `Range`), `POST /api/messages/{id}/stop`, `POST /api/messages/{id}/regenerate`.
**Ngoài:** xóa tài liệu (làm sau; nhưng mã `410 DOCUMENT_DELETED` phải sẵn sàng khi có).

## 3. Đặc tả chi tiết

### 3.1 `GET /api/citations/{message_id}/{index}`
- Tin nhắn phải thuộc **hội thoại của người gọi**, ngược lại `404 NOT_FOUND`. `index` không có trong `citations` → `404`.
- Tài liệu của trích dẫn đã xóa mềm (`deleted_at IS NOT NULL`) → `410 DOCUMENT_DELETED` (`message_key=source.deleted`).
- Ngược lại `200 CitationDetail`:
  ```json
  {"document_id":"…","document_name":"HopDong_A.pdf","page":12,"page_count":48,
   "highlight":{"snippet":"Bên B thanh toán giá trị đợt 1 trong vòng 30 …","page_text_start":1180,"page_text_end":1890}}
  ```
  `snippet` = tối đa 300 ký tự đầu của **chunk** đã trích (văn bản chuẩn hóa từ `chunks.text`); `page_text_start/end` = `chunks.char_start/char_end` trên văn bản trang. `highlight` là `null` nếu không tìm thấy chunk (dữ liệu cũ). Client dùng `snippet` để tìm đoạn trong lớp văn bản của pdf.js (T18); vị trí ký tự chỉ là gợi ý bổ sung vì cách tách văn bản của PyMuPDF và pdf.js có thể khác nhau.
- Ghi analytics **không** nằm ở đây (EV-030 do client gửi).

### 3.2 `GET /api/documents/{id}/file`
- Mọi người dùng đã đăng nhập đọc được mọi tài liệu của workspace (D-001); không tồn tại/đã xóa → `404`/`410`.
- Phát nguyên file từ `StoragePort` theo luồng (không nạp cả file vào bộ nhớ).
- **Hỗ trợ `Range`** (pdf.js tải theo byte-range): `Accept-Ranges: bytes`; một khoảng `bytes=a-b`, `a-`, `-n` → `206 Partial Content` + `Content-Range`; khoảng không hợp lệ → `416`. Không hỗ trợ nhiều khoảng.
- Header: `Content-Type: application/pdf`, `Content-Disposition: inline; filename*=UTF-8''<tên đã mã hóa phần trăm>`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.

### 3.3 `POST /api/messages/{id}/stop`
Tin nhắn phải thuộc người gọi (`404` nếu không). **Idempotent**, trả `204` kể cả khi đã kết thúc. Nếu `status=streaming`: hủy tác vụ sinh (T13/T14), đóng luồng LLM, đặt `status=stopped`, **giữ phần câu trả lời đã có và trích dẫn đã phát**, ghi `completed_at`. Ghi analytics **EV-022** `chat_answer_stopped {question_id, elapsed_ms}`. Kết nối SSE đang mở được đóng sau khi lưu xong. Yêu cầu: hiệu lực ≤ 1 giây.

### 3.4 `POST /api/messages/{id}/regenerate`
- Chỉ khi `status ∈ {stopped, failed}`; ngược lại `409 NOT_REGENERABLE`.
- **Dùng lại cùng `message_id`:** xóa `answer`, `citations`, `missing`, `error_code`, `answer_type`; đặt `status=streaming`; chạy lại đúng quy trình 3.2 của T14 từ bước truy hồi với `question` đã lưu; trả SSE cùng định dạng (`meta` có cùng `message_id`). Kiểm tra lại "có tài liệu `ready`" (`409 NO_READY_DOCUMENTS`) và đồng thời (`409 ANSWER_IN_PROGRESS`).
- Ghi analytics **EV-025** `chat_answer_regenerated {question_id, previous_outcome}` (`previous_outcome` = `stopped|failed:<error_code>`).

## 4. Tệp sẽ tạo
`routers/citations.py`, mở rộng `routers/documents.py` (file), `routers/chat.py` (stop/regenerate), `services/range_response.py`, `tests/test_citations_*.py`, `tests/test_file_range.py`, `tests/test_stop_regenerate.py`.

## 5. Tiêu chí hoàn thành
- [ ] AC-030.1 (phần backend): với câu trả lời có trích dẫn, `GET /citations/...` trả đúng tài liệu, trang, `page_count`, `snippet` thuộc đúng trang đó.
- [ ] AC-030.3: sau khi tài liệu bị xóa mềm (dựng bằng cách đặt `deleted_at` trong test) → `410 DOCUMENT_DELETED`; `GET /documents/{id}/file` cũng `410`.
- [ ] Hội thoại của người khác → `404` cho cả `citations` và `stop`/`regenerate`.
- [ ] `Range`: `bytes=0-99` → `206` đúng 100 byte và `Content-Range`; `bytes=100-` đến hết file; `bytes=-50` 50 byte cuối; vượt cỡ file → `416`; không có `Range` → `200` toàn bộ; nội dung ghép các khoảng khớp file gốc (so sánh băm).
- [ ] Tên file tiếng Việt có dấu/khoảng trắng trong `Content-Disposition` được mã hóa đúng (kiểm tra header); có `no-store` và `nosniff`.
- [ ] Người chưa đăng nhập → `401` cho mọi endpoint.
- [ ] Stop: gọi khi đang streaming → `status=stopped`, phần đã có giữ nguyên, tác vụ LLM bị hủy trong ≤ 1 giây (đồng hồ giả); gọi lần hai/khi đã xong → vẫn `204`, không đổi dữ liệu.
- [ ] Stop + SSE đang mở: kết nối được đóng; không rò rỉ tác vụ.
- [ ] Regenerate: từ `stopped` và từ `failed` thành công → `meta.message_id` giữ nguyên, dữ liệu cũ bị xóa, kết quả mới được lưu; từ `completed` → `409 NOT_REGENERABLE`.
- [ ] EV-022 và EV-025 ghi đúng thuộc tính, không chứa nội dung.

## 6. Kiểm thử
pytest + `FakeLLM`; test `Range` bằng file 1 MB tạo ngẫu nhiên; đồng hồ giả cho giới hạn 1 giây; test quyền truy cập chéo hội thoại.

## 7. Ghi chú
Nếu OQ-UX-01 trả lời rằng không thể làm nổi bật theo vị trí, `snippet` vẫn đủ cho cách tìm chuỗi phía client (T18) — đó đã là phương án chính ở thiết kế này.
