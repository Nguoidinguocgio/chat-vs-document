# T14 — Backend: API hội thoại và hỏi đáp SSE

| | |
|---|---|
| **Giai đoạn / Loại** | 2 — BB-02 Hỏi đáp / Backend |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T09 (`emit`), T13 (và T12, T04) |
| **Mở khóa** | T17, T20; tích hợp thật cho T15/T16 |
| **Liên quan** | US-020 (AC-020.1 – 020.4), US-021 – US-024, US-040/041 (phần đọc cần thiết); D-001; EV-020, EV-023, EV-024 |

## 1. Mục tiêu
Cho client tạo hội thoại, gửi câu hỏi và nhận câu trả lời **theo luồng SSE**; lưu lại đầy đủ (câu hỏi, câu trả lời, trích dẫn, kết quả, thời gian, token) để xem lại và đo lường. Hội thoại là **riêng tư** của từng người.

## 2. Phạm vi
**Trong:** `POST/GET /api/conversations`, `GET /api/conversations/{id}`, `POST /api/conversations/{id}/messages` (SSE), lưu trữ, đồng thời, analytics.
**Ngoài:** Dừng/Tạo lại (T17); xóa hội thoại, tự xóa sau 90 ngày, ngữ cảnh nhiều lượt (mỗi câu hỏi độc lập ở MVP lõi); đánh giá 👍/👎.

## 3. Đặc tả chi tiết

### 3.1 Hội thoại
| Endpoint | Hành vi |
|---|---|
| `POST /api/conversations` | Tạo hội thoại rỗng của người gọi, `title="Hội thoại mới"` → `201 Conversation` |
| `GET /api/conversations` | Hội thoại **của người gọi**, `last_activity_at` giảm dần, tối đa 50 mục → `Conversation[]` |
| `GET /api/conversations/{id}` | `{conversation, messages:Message[]}` kèm `citations`; hội thoại của người khác hoặc không tồn tại → `404 NOT_FOUND` (không phân biệt; không ai, kể cả Admin, đọc được hội thoại người khác qua API này) |

### 3.2 `POST /api/conversations/{id}/messages`
Body `{question: string}`. Kiểm tra **trước khi mở luồng** (lỗi trả JSON chuẩn, không phải SSE):
- `question` sau `trim` rỗng hoặc dài hơn 2.000 ký tự → `422 QUESTION_INVALID`.
- Hội thoại không thuộc người gọi → `404`.
- Không có tài liệu `ready` nào → `409 NO_READY_DOCUMENTS`.
- Hội thoại đang có câu trả lời chưa kết thúc (`messages.status='streaming'`) → `409 ANSWER_IN_PROGRESS`.

Sau đó trả `200 text/event-stream` (`Cache-Control: no-cache`, `X-Accel-Buffering: no`) và chạy:
1. Chèn `messages` (`status=streaming`, `question`, `lang=detect_lang`); nếu là câu hỏi đầu tiên: `title = 40 ký tự đầu` của câu hỏi (cắt ở ranh giới từ, thêm "…").
2. Gửi `meta {message_id, lang}` rồi `status {phase:"retrieving"}`.
3. `Retriever.search` (T12). Rỗng → **không gọi LLM**: gửi `done {answer_type:"not_found"}`, lưu `answer_type=not_found`, `status=completed`.
4. Ngược lại `AnswerService.stream` (T13): chuyển từng `AnswerEvent` thành khung SSE (`delta`, `citation` — kèm `document_deleted=false`, `done`, `error`). Đo `ttft_ms` ở `delta` đầu tiên.
5. Lưu **theo giai đoạn**: nối văn bản vào `messages.answer` (ghi định kỳ ≈ mỗi 1 giây và khi kết thúc để không mất nếu máy chủ chết); chèn `citations` (`idx`, `document_id`, `page`, `chunk_id`) khi nhận `Citation`.
6. Kết thúc: `done` → `status=completed`, `answer_type`, `missing`, `completed_at`, `total_ms`, token; `Failed(code)` → `status=failed`, `error_code`, gửi `error {code}`.
7. Cập nhật `conversations.last_activity_at`.

**Đảm bảo kết thúc:** luôn đúng một `done` hoặc `error`, **kể cả** khi LLM im lặng (T13 đã đặt hạn 30 giây) hoặc nhà cung cấp lỗi. Không bao giờ treo vô thời hạn (AC-020.2).

**Ngắt kết nối của client** (không qua `stop`): hủy tác vụ sinh, đặt `status=stopped`, giữ phần đã có.

**Mô phỏng mất mạng (chỉ khi `LLM_PROVIDER=fake`):** nếu `FakeLLM` ném `SimulateConnectionDrop` (T13), đóng kết nối SSE ngay, **không** gửi `done`/`error`, đặt `status=failed`, `error_code=llm_error`. Đây là cách duy nhất để E2E tạo ra trạng thái "Gián đoạn".

### 3.3 Khung SSE
```
event: meta
data: {"message_id":"…","lang":"vi"}

event: delta
data: {"text":"Hai tài liệu "}

event: citation
data: {"index":1,"document_id":"…","document_name":"HopDong_A.pdf","page":12,"document_deleted":false}

event: done
data: {"answer_type":"answered"}
```
`data` luôn là JSON một dòng (thoát `\n` trong văn bản). Thêm dòng `: ping` mỗi 10 giây khi chưa có sự kiện để proxy không đóng kết nối.

### 3.4 Đồng thời và giới hạn
- Một câu trả lời đang chạy mỗi hội thoại (409 ở trên). Giới hạn thêm **3 câu trả lời đồng thời mỗi người dùng** (vượt → `409 ANSWER_IN_PROGRESS`) và **tối đa 20 câu trả lời đồng thời toàn hệ thống** (cấu hình được): vượt thì yêu cầu chờ tối đa 10 giây trong hàng đợi, quá hạn thì gửi `error {code:"rate_limited"}`. MVP không thêm mã lỗi HTTP mới.
- Câu hỏi không liên quan ("xin chào", "kiểm tra") vẫn đi qua đường chung; LLM trả lời ngắn giới thiệu chức năng hoặc NOT_FOUND (PRD §5.2) — không coi là lỗi.

### 3.5 Analytics (qua `emit`, T09)
**EV-020** `chat_question_submitted` `{conversation_id, turn_index, question_length, question_lang, ready_document_count}` — ngay khi nhận hợp lệ. **EV-023** `chat_answer_completed` `{question_id, total_ms, answer_type, citation_count, distinct_document_count, llm_input_tokens, llm_output_tokens}` khi `done`. **EV-024** `chat_answer_failed` `{question_id, error_code}` khi lỗi. Không ghi nội dung.

## 4. Tệp sẽ tạo
`routers/chat.py`, `services/conversations.py`, `services/chat_stream.py`, `tests/test_chat_*.py`.

## 5. Tiêu chí hoàn thành
Dùng `FakeLLM`/`FakeEmbedding`:
- [ ] AC-020.1 (phần backend): luồng `meta → status → delta… → citation… → done` đúng thứ tự; `ttft_ms` được ghi.
- [ ] AC-020.2: các kịch bản `thanh toan`, `doanh thu`, `phap`, `ngat`, `cham`, `loi dich vu` — **mỗi luồng đều kết thúc**: bằng `done` hoặc `error`, riêng kịch bản `ngat` kết thúc bằng việc đóng kết nối đột ngột (3.2) — client phải xử lý như "Gián đoạn"; không luồng nào treo (đồng hồ giả).
- [ ] AC-020.4 (phần backend): không có tài liệu `ready` → `409 NO_READY_DOCUMENTS`.
- [ ] AC-023.1: truy hồi rỗng → `done not_found` **không gọi LLM** (kiểm tra bộ đếm lời gọi FakeLLM = 0).
- [ ] AC-022.1: trích dẫn được lưu vào `citations` với đúng tài liệu và trang; truy xuất lại qua `GET /conversations/{id}` khớp luồng đã phát.
- [ ] Câu hỏi 0 và 2.001 ký tự → `422 QUESTION_INVALID`; 2.000 ký tự hợp lệ.
- [ ] Hội thoại của người khác: `GET` và `POST` đều `404`; Admin cũng không đọc được.
- [ ] Gửi câu hỏi thứ hai khi câu trước chưa xong → `409 ANSWER_IN_PROGRESS`.
- [ ] Ngắt kết nối giữa chừng → tác vụ sinh bị hủy, `status=stopped`, phần đã có được giữ; không còn tác vụ nền (kiểm tra).
- [ ] Máy chủ khởi động lại khi có message `streaming` → các message đó được đánh dấu `failed` (`error_code=llm_error`) lúc khởi động.
- [ ] EV-020, EV-023, EV-024 được ghi đúng thuộc tính; không chứa nội dung; log không chứa câu hỏi/đáp án.
- [ ] Tiêu đề hội thoại lấy từ câu hỏi đầu tiên, cắt gọn.
- [ ] Người chưa đăng nhập → `401`; thiếu `X-Requested-With` → `403`.

## 6. Kiểm thử
pytest với client SSE (httpx stream) + `FakeLLM`; đồng hồ giả cho hạn 30 giây; test đồng thời hai yêu cầu; test hủy bằng cách đóng kết nối.

## 7. Ghi chú
Nếu OQ-UX-02 xác nhận proxy đệm SSE, thử gửi `delta` theo mảnh nhỏ cùng `: ping` thường xuyên hơn; không đổi giao thức.
