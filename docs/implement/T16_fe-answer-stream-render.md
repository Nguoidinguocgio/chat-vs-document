# T16 — Frontend: nhận luồng và hiển thị câu trả lời

| | |
|---|---|
| **Giai đoạn / Loại** | 2 — BB-02 Hỏi đáp / Frontend |
| **Ước tính** | 5 ngày |
| **Phụ thuộc** | T15, T06 (MSW SSE giả); tích hợp thật với T14 |
| **Mở khóa** | T18, T19 |
| **Liên quan** | US-020, US-021, US-022 (AC-022.1), US-023 (AC-023.1 – 023.3), US-024 (AC-024.1); SDD ALG-01, ST-02, CMP-05, CMP-06, 3.5.4 (SSE), 3.5.2 (aria-live); NFR-S05; prototype SCR-03 |

## 1. Mục tiêu
Câu trả lời hiện ra **dần dần, mượt, an toàn**, với trích dẫn `[n]` bấm được, danh sách nguồn, và cách trình bày **khác nhau rõ ràng** cho 4 kết quả: có căn cứ, một phần, không tìm thấy (và đang tìm/đang viết). Nội dung do LLM sinh được coi là **không tin cậy**.

## 2. Phạm vi
**Trong:** `useAnswerStream`, bộ phân tích SSE, máy trạng thái ST-02 (nhánh thành công), `AnswerRenderer`, `CitationChip`, `AnswerCard`, `SourceList`, thông báo trình đọc màn hình, analytics EV-021.
**Ngoài:** Dừng/Tạo lại, trạng thái gián đoạn/quá hạn/lỗi (T19 hoàn thiện — nhưng **máy trạng thái ở đây phải sẵn chỗ cho chúng**); mở bảng Xem nguồn (T18, chỉ phát sự kiện).

## 3. Đặc tả chi tiết

### 3.1 Bộ phân tích SSE (`parseSseStream`)
Hàm thuần nhận `ReadableStream<Uint8Array>`, trả `AsyncIterator<{event, data}>`:
- Giải mã bằng `TextDecoder('utf-8', {stream: true})` (**không** mất ký tự tiếng Việt khi một ký tự bị cắt giữa hai mảnh byte).
- Tách khung theo dòng trống (`\n\n` hoặc `\r\n\r\n`); trong khung: dòng `event:`, `data:` (nhiều dòng `data:` nối bằng `\n`), `id:`; bỏ qua dòng bắt đầu bằng `:` (ping).
- `data` được `JSON.parse`; JSON lỗi → bỏ khung đó, ghi cảnh báo (dev), không làm hỏng luồng.

### 3.2 `useAnswerStream` (ALG-01)
- Gửi: `fetch('POST /api/conversations/{id}/messages', {signal, headers:{'X-Requested-With':'fetch', Accept:'text/event-stream'}})` qua `shared/api` (hàm `openStream` trả `ReadableStream`; vẫn là nơi duy nhất gọi `fetch`). Lỗi trước luồng (JSON chuẩn): `422 QUESTION_INVALID` → hiển thị `chat.too_long`/câu hỏi rỗng; `409 NO_READY_DOCUMENTS` → làm mới tóm tắt tài liệu (Composer tự khóa); `409 ANSWER_IN_PROGRESS` → bỏ qua.
- Ngay khi người dùng gửi (**không chờ máy chủ**): thêm `QuestionBubble` và `AnswerCard` ở trạng thái `searching` (≤ 300 ms, "Đang tìm trong tài liệu…").
- Reducer theo ST-02: `searching → streaming` (khi `delta` đầu tiên) `→ answered | partial | notfound` (theo `done.answer_type`). Mọi chuyển trạng thái khác (`stopped`, `interrupted`, `timeout`, `failed`) có **chỗ trong kiểu dữ liệu** ngay từ bây giờ; T19 nối hành vi.
- **Gộp `delta`:** đưa văn bản vào bộ đệm; mỗi `requestAnimationFrame` nối bộ đệm vào nội dung một lần (không render theo từng token).
- `citation` → lưu vào bảng `citations[index]`.
- `done` → chốt trạng thái, hiện danh sách nguồn, mở lại Composer, xóa bản nháp, đặt focus ô nhập; `error` → chuyển trạng thái lỗi tương ứng (hiển thị đầy đủ ở T19; tạm thời hiển thị thông điệp + nút "Thử lại" ở mức tối thiểu).
- **Giám sát im lặng:** bộ đếm 30 giây, đặt lại sau **mỗi** sự kiện hoặc `ping`; hết hạn → hủy luồng, trạng thái `timeout`.
- Luồng đóng mà không có `done`/`error` → trạng thái `interrupted`.
- Analytics: **EV-021** `chat_answer_first_token {question_id, ttft_ms}` đo ở client (từ lúc gửi đến `delta` đầu tiên).
- Mỗi lần chỉ một luồng mỗi hội thoại; hủy luồng khi rời hội thoại hoặc đóng trang.

### 3.3 `AnswerRenderer` và an toàn nội dung (R-4, NFR-S05)
- Chỉ `shared/lib/markdown` được biến chuỗi thành phần tử DOM. Dùng `markdown-it` (`html: false`, `linkify: false`) rồi **DOMPurify** (`ALLOWED_TAGS`, `ALLOWED_ATTR: []`) với **danh sách trắng**: `p, br, strong, em, ul, ol, li, blockquote, code, pre, table, thead, tbody, tr, th, td, h3, h4`. **Cấm** `img`, `a` (liên kết hiển thị dạng văn bản thường), `script`, `iframe`, `style`, mọi thuộc tính `on*`, `style`, `href`, `src`. HTML thô trong Markdown bị loại bỏ (`html: false`). Kết quả đã làm sạch được gắn vào DOM bằng `v-html` **duy nhất** trong `AnswerMarkdown.vue` (ngoại lệ có chú thích của luật R-4).
- **Chip trích dẫn:** một plugin biến token `[n]` trong **nút văn bản** thành `CitationChip` **chỉ khi** đã nhận sự kiện `citation` có `index = n`; chưa có hoặc ngoài phạm vi → giữ văn bản thường. Không biến `[n]` trong khối `code`.
- Đang streaming: con trỏ nhấp nháy ở cuối **phần tử inline cuối cùng** (nếu đoạn cuối là danh sách thì ở cuối mục cuối, không xuống dòng riêng); tắt nhấp nháy khi `prefers-reduced-motion`.
- Chỉ render lại **khối Markdown cuối cùng** khi có `delta` (các khối đã hoàn chỉnh được memo hóa).
- Thuộc tính `lang` của khối trả lời = `meta.lang`.

### 3.4 `CitationChip` (CMP-06)
Nút `[n]` (≥ 24×24 px, cách nhau ≥ 4 px); `aria-label="Trích dẫn {n}: {tên tài liệu}, trang {p}"`; `title` giống nhãn; nếu tài liệu đã bị xóa (`document_deleted` hoặc tài liệu không còn trong danh sách) → kiểu gạch ngang + "Tài liệu đã bị xóa". Bấm → phát sự kiện UI `citation:open {messageId, index}` qua `app` (T18 xử lý; luật R-2: không import chéo feature).

### 3.5 `AnswerCard` (CMP-05) và `SourceList`
| Trạng thái | Trình bày (khớp prototype) |
|---|---|
| `searching` | spinner + `chat.searching` |
| `streaming` | văn bản + con trỏ |
| `answered` | văn bản + **SourceList** (chưa có 👍/👎 — US-051 ngoài phạm vi) |
| `partial` | như `answered` + khối ⓘ "**Không tìm thấy:** {missing}" |
| `notfound` | khối ⓘ `chat.not_found` + gợi ý "kiểm tra tài liệu liên quan đã được tải lên và ở trạng thái Sẵn sàng"; **không** có SourceList |

`SourceList`: tiêu đề "NGUỒN", mỗi dòng `[n] {tên tài liệu}, trang {p}` bấm được (cùng hành vi chip); chỉ liệt kê các `n` **thực sự xuất hiện** trong văn bản và có dữ liệu trích dẫn; tài liệu đã xóa → "Tài liệu đã bị xóa". **Bất biến:** chỉ `answered`/`partial` có SourceList.

### 3.6 Thông báo trình đọc màn hình
Vùng `aria-live="polite"` ẩn riêng: khi bắt đầu "Đang tạo câu trả lời"; khi kết thúc "Câu trả lời đã xong, có {n} trích dẫn" hoặc "Không tìm thấy thông tin". Trong lúc streaming, thẻ trả lời có `aria-busy="true"` và **không** đọc từng token.

## 4. Tệp sẽ tạo
`shared/api/stream.ts`, `shared/lib/sse/parseSseStream.ts`, `shared/lib/markdown/{AnswerMarkdown.vue, citationPlugin.ts, sanitizeConfig.ts}`, `features/chat/{useAnswerStream.ts, answerReducer.ts, AnswerCard.vue, CitationChip.vue, SourceList.vue, QuestionBubble.vue}`, `*.test.ts`.

## 5. Tiêu chí hoàn thành
- [ ] `parseSseStream`: kết quả không đổi khi luồng byte bị cắt **ở mọi vị trí** (kể cả giữa một ký tự UTF-8 nhiều byte như "ế", "ượ" và giữa `\n\n`); xử lý `data:` nhiều dòng; bỏ qua `: ping`; JSON lỗi không làm sập luồng.
- [ ] AC-020.1 (giao diện): thẻ "Đang tìm…" xuất hiện ≤ 300 ms sau khi gửi (đo bằng đồng hồ giả/E2E); token đầu hiển thị ngay khi nhận; EV-021 được gửi một lần với `ttft_ms` hợp lý.
- [ ] AC-022.1: câu trả lời `answered` hiển thị chip `[n]` và danh sách "Nguồn: [n] tên, trang p" cuối thẻ; chip chỉ xuất hiện sau khi nhận `citation` tương ứng.
- [ ] AC-021.2/021.3: câu trả lời mẫu hai tài liệu mâu thuẫn hiển thị `[1]` và `[2]` trỏ đúng hai tài liệu.
- [ ] AC-023.1/023.2: `done not_found` → thông điệp `chat.not_found` + gợi ý, **không** có danh sách nguồn.
- [ ] AC-023.3: `done partial` → văn bản có trích dẫn + khối "Không tìm thấy: …".
- [ ] AC-024.1: `lang` của khối trả lời đúng; văn bản tiếng Việt có dấu hiển thị đúng khi bị cắt mảnh.
- [ ] **An toàn (NFR-S05, QS-04):** bộ **≥ 10 payload** (`<img src=x onerror=…>`, `<script>`, `[x](javascript:…)`, `<iframe>`, `<svg onload>`, Markdown ảnh, `<a href>`, khối HTML thô, `[1](javascript:…)`, chuỗi dài cực lớn) đi qua cả luồng SSE giả và `AnswerRenderer` → **không** có script thực thi, không có phần tử `img/a/iframe/script` trong DOM, không có thuộc tính `on*`/`style`.
- [ ] Hiệu năng: với câu trả lời 3.000 ký tự phát từng 2 ký tự, không khung hình nào > 50 ms (đo bằng Performance API trong test) vì gộp theo `requestAnimationFrame` và chỉ render lại khối cuối.
- [ ] Im lặng 30 giây không có sự kiện → trạng thái `timeout` (đồng hồ giả); luồng đóng không có `done` → `interrupted`.
- [ ] Một luồng mỗi hội thoại; rời trang hủy luồng (không rò rỉ).
- [ ] Trình đọc màn hình: không đọc từng token; thông báo hoàn tất đúng; axe-core 0 lỗi serious/critical; khớp prototype.
- [ ] Lint R-3/R-4 xanh (không `fetch` ngoài `shared/api`, không `v-html`/`innerHTML` ngoài `shared/lib/markdown`).

## 6. Kiểm thử
Vitest cho `parseSseStream` (kể cả thuộc tính ngẫu nhiên), `answerReducer`, plugin trích dẫn; Vue Test Utils/Testing Library cho `AnswerCard` từng trạng thái; MSW SSE giả cho luồng đầy đủ; bộ payload XSS dùng lại ở T22.

## 7. Ghi chú
`done` có thể đến trước khi các `citation` cuối được xử lý nếu máy chủ đảo thứ tự — reducer phải nhận `citation` **muộn** hợp lệ trước khi thẻ chốt, hoặc chip chỉ thành nút khi có dữ liệu (đã bảo đảm bởi 3.3). Hợp đồng T06 cam kết `citation` luôn đến trước `done`.

**Ghi chú khi hiện thực (2026-10-28):**
- **Lệch phạm vi:** không có `CitationChip.vue`. HTML đã làm sạch không mount được component Vue, nên `shared/lib/markdown/citationPlugin.ts` biến `[n]` thành `<button class="md-cite">` bằng DOM API **sau** DOMPurify (chỉ khi đã có `citation` n, bỏ qua `code/pre`); `features/chat/CitationChip.ts` quyết định nhãn/kiểu gạch ngang. Liên kết/ảnh Markdown tắt ngay ở markdown-it.
- `parseSseStream` dùng lại `SseParser` (T06); sửa lỗi của `SseParser` khi mảnh bị cắt giữa `\r` và `\n` (phát sự kiện sớm). Nhận `signal` để hủy reader vì một số lớp mạng (MSW) không tự cắt body khi abort.
- Giám sát im lặng tính cả `: ping` (bất kỳ byte nào); thời gian trong `streamConfig.silenceMs` để test rút ngắn thay cho đồng hồ giả. Hiệu năng: 3.000 ký tự × 2 ký tự/lần, mỗi lần cập nhật < 50 ms trong jsdom (chỉ trang trí lại khối đổi).
- Sự kiện `citation:open` đi lên `app` (`layoutStore.openCitation`); bảng Xem nguồn làm ở T18. Lỗi/Thử lại ở mức tối thiểu (hỏi lại cùng câu) — T19 hoàn thiện.
