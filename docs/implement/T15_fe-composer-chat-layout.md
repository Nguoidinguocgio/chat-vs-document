# T15 — Frontend: ô nhập, khung chat, danh sách hội thoại

| | |
|---|---|
| **Giai đoạn / Loại** | 2 — BB-02 Hỏi đáp / Frontend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T05, T06 (MSW), T11 (`useDocumentsSummary`, `RightPanel`) |
| **Mở khóa** | T16 |
| **Liên quan** | US-020 (AC-020.3, 020.4), US-001 (AC-001.5 — nháp); SDD CMP-03, CMP-04, CMP-07, CMP-08, CMP-09, ST-04, 3.5.2 (bàn phím); prototype SCR-03 |

## 1. Mục tiêu
Khung chat mà người dùng thấy ngay khi vào ứng dụng: biết rõ **được hỏi hay chưa và vì sao**, gõ câu hỏi thoải mái (kể cả tiếng Việt với bộ gõ) mà không mất nội dung, và có danh sách hội thoại để quay lại.

## 2. Phạm vi
**Trong:** `ChatPane`, `MessageList` (khung, cuộn), `Composer`, `Disclaimer`, câu hỏi gợi ý, `ConversationSidebar` (đọc + tạo mới), route `/c` và `/c/:id`, phím tắt.
**Ngoài:** nhận và hiển thị câu trả lời (T16); Dừng/Tạo lại (T19); xóa hội thoại, nhãn sắp hết hạn (làm sau).

## 3. Đặc tả chi tiết

### 3.1 Route và dữ liệu
- `/c` luôn là "hội thoại mới" (chưa tạo ở máy chủ cho đến khi gửi câu hỏi đầu tiên: gọi `POST /api/conversations` rồi `POST …/messages`; sau đó thay URL bằng `/c/:id` mà không tải lại trang).
- `/c/:id`: `GET /api/conversations/{id}` → hiển thị toàn bộ tin nhắn; `404` → trang "Không tìm thấy hội thoại" (SCR-06).
- `useConversations()` = `GET /api/conversations` (làm mới sau khi gửi câu hỏi đầu tiên và khi quay lại tab).

### 3.2 `Composer` (CMP-07) và trạng thái ST-04
Hook `useComposerState()` **suy ra** (không lưu) trạng thái theo thứ tự ưu tiên, gộp `navigator.onLine`, `useDocumentsSummary()` (T11), trạng thái câu trả lời hiện tại (T16 cung cấp `isAnswering`), nội dung ô nhập:

| # | Điều kiện | Trạng thái | Trình bày |
|---|---|---|---|
| 1 | offline | `offline` | giữ nội dung; Gửi vô hiệu hóa; dòng "Đang mất kết nối" |
| 2 | không có tài liệu | `no_documents` | ô nhập vô hiệu hóa; `chat.empty_workspace` + nút "Tải lên" |
| 3 | chỉ có tài liệu đang xử lý | `processing_only` | vô hiệu hóa; `chat.all_processing` |
| 4 | tất cả tài liệu lỗi | `all_failed` | vô hiệu hóa; `chat.all_failed` + liên kết mở bảng Tài liệu |
| 5 | câu trả lời chưa kết thúc | `busy` | vẫn gõ được; nút Gửi → **Dừng** (hành động Dừng do T19 gắn) |
| 6 | nội dung rỗng/chỉ khoảng trắng | `empty` | nút Gửi vô hiệu hóa |
| 7 | > 2.000 ký tự | `too_long` | bộ đếm đỏ + `chat.too_long`; Gửi vô hiệu hóa |
| 8 | còn lại | `ready` | gửi được |

Chi tiết:
- `textarea` tự giãn, tối đa 8 dòng, 16 px; `aria-label="Câu hỏi"`, `aria-describedby` trỏ tới dòng trạng thái.
- **`Enter` gửi, `Shift+Enter` xuống dòng.** Không gửi khi đang **gõ tiếng Việt bằng bộ gõ (IME)**: bỏ qua `Enter` nếu `event.isComposing` hoặc `keyCode === 229`.
- Bộ đếm "n/2000" chỉ hiện khi ≥ 1.800 ký tự; đỏ khi > 2.000.
- Khi gửi: xóa ô nhập, xóa bản nháp, đặt focus lại ô nhập khi câu trả lời kết thúc.
- Nút Gửi có `aria-label` ("Gửi câu hỏi" / "Dừng tạo câu trả lời"); vùng bấm ≥ 40 px.
- **Bản nháp:** `saveDraft` (debounce 500 ms) mỗi lần gõ; nạp lại khi vào hội thoại/đăng nhập lại (AC-001.5); `/c` mới dùng khóa `conversationId='new'`. Nháp **không** đi vào analytics.
- `Disclaimer` (CMP-08) cố định dưới ô nhập: `chat.disclaimer`, hiển thị ở **mọi** trạng thái.

### 3.3 `MessageList` (CMP-04)
- `role="log"`, `aria-label="Hội thoại"`, `aria-live` **tắt** (thông báo hoàn tất do T16 phát qua vùng live riêng).
- Tự cuộn xuống khi có nội dung mới **trừ khi** người dùng đã cuộn lên (khoảng cách tới đáy > 60 px); khi đó hiện nút "↓ Câu trả lời mới".
- Khi chưa có tin nhắn: `EmptyWorkspaceState` (T11) nếu chưa hỏi được; nếu hỏi được: lời chào "Bạn muốn tìm gì trong tài liệu?" + **3 câu hỏi gợi ý** (`SuggestedQuestions`): "Tóm tắt các điều khoản thanh toán", "So sánh các mốc thời gian giữa các tài liệu", "Doanh thu quý 3 là bao nhiêu?". Bấm gợi ý **chỉ điền vào ô nhập**, không tự gửi (DD-06).
- Khung chứa nội dung rộng tối đa 760 px, căn giữa.

### 3.4 `ConversationSidebar` (CMP-03, phạm vi tối thiểu)
Nút "＋ Hội thoại mới" (→ `/c`, `Ctrl+Shift+O`); danh sách nhóm theo "Hôm nay / 7 ngày trước / 30 ngày trước / Cũ hơn" (theo `last_activity_at`, múi giờ `Asia/Ho_Chi_Minh`), mục đang mở có `aria-current="true"`; tiêu đề cắt bằng "…"; trống → `conversations.empty`. Skeleton khi tải > 300 ms.

### 3.5 Phím tắt và tiếp cận
`/` (khi không ở trong ô nhập) → focus ô nhập · `Ctrl+Shift+O` → hội thoại mới · liên kết "Bỏ qua tới ô nhập câu hỏi" đã có từ T05 · thứ tự Tab: thanh trên → thanh trái → danh sách tin nhắn → ô nhập → bảng phải.

## 4. Tệp sẽ tạo
`features/chat/{ChatPage, ChatPane, MessageList, Composer, useComposerState, Disclaimer, SuggestedQuestions, useConversationRoute}.tsx`, `features/conversations/{ConversationSidebar, useConversations}.tsx`, `*.test.tsx`.

## 5. Tiêu chí hoàn thành
- [ ] AC-020.4: không có tài liệu `ready` → ô nhập vô hiệu hóa, hiển thị "Tải lên ít nhất một tài liệu PDF để bắt đầu hỏi" + nút "Tải lên"; ba biến thể `no_documents` / `processing_only` / `all_failed` đúng.
- [ ] AC-020.3 (giao diện): khi `isAnswering`, không gửi được câu mới; nút chuyển thành "Dừng".
- [ ] Bảng ST-04: test từng dòng và **thứ tự ưu tiên** (ví dụ vừa offline vừa quá dài → hiển thị `offline`).
- [ ] 2.000 ký tự gửi được; 2.001 bị chặn với thông báo và bộ đếm đỏ; chỉ khoảng trắng → Gửi vô hiệu hóa.
- [ ] `Enter` gửi, `Shift+Enter` xuống dòng; **không** gửi khi đang soạn dấu bằng IME (test mô phỏng `compositionstart/end` và `isComposing`).
- [ ] AC-001.5: gõ dở → hết phiên → đăng nhập lại → nội dung có lại; đăng xuất chủ động → nháp bị xóa.
- [ ] Bấm gợi ý chỉ điền vào ô nhập, không gửi.
- [ ] `Disclaimer` hiển thị ở mọi trạng thái Composer.
- [ ] Danh sách hội thoại nhóm đúng theo thời gian, có `aria-current`; tạo mới hoạt động; `/c/:id` của người khác/không tồn tại → 404.
- [ ] Cuộn thông minh: đang cuộn lên đọc thì không bị kéo xuống; có nút "↓ Câu trả lời mới".
- [ ] Phím tắt hoạt động; axe-core 0 lỗi serious/critical; khớp prototype ở 1440 px và 1100 px.

## 6. Kiểm thử
Vitest + Testing Library (hook `useComposerState` thuần, hành vi bàn phím/IME), MSW cho tải hội thoại; Playwright (T21) cho luồng nháp qua hết phiên.

## 7. Ghi chú
Tab thứ hai mở cùng hội thoại: mỗi tab độc lập; nếu một tab gửi câu hỏi thì tab kia chỉ thấy khi làm mới — chấp nhận ở MVP (PRD §5.2: hội thoại hoạt động độc lập).
