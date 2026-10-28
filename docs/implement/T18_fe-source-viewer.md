# T18 — Frontend: bảng Xem nguồn (pdf.js)

| | |
|---|---|
| **Giai đoạn / Loại** | 3 — Kiểm chứng và độ bền / Frontend |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T16 (chip trích dẫn), T11 (`RightPanel`); dùng MSW nếu T17 chưa xong |
| **Mở khóa** | T21 |
| **Liên quan** | US-030 (AC-030.1 – 030.3); SDD CMP-12, ST-05, IS-02, DD-02, ngân sách 3.8.1 / mục 4; EV-030; prototype SCR-03 (bảng Xem nguồn) |

## 1. Mục tiêu
Bấm `[n]` → trong **≤ 1,5 giây** thấy đúng trang PDF với **đoạn đã dùng được làm nổi bật** — để người dùng kiểm chứng mà không phải tự mở file. Đây là khoảnh khắc "aha" của sản phẩm.

## 2. Phạm vi
**Trong:** khe `source` của `RightPanel`, tải `pdfjs-dist` lười, vẽ một trang, lớp văn bản, thuật toán tìm và làm nổi bật `snippet`, điều hướng trang, trạng thái tải/xóa/lỗi, quản lý focus, analytics EV-030.
**Ngoài:** xem toàn tài liệu liên tục, tìm kiếm trong tài liệu, tải xuống.

## 3. Đặc tả chi tiết

### 3.1 Mở bảng
- `app` lắng nghe sự kiện UI `citation:open {messageId, index}` (từ `CitationChip`/`SourceList`, T16): đặt `panel='source'` (đóng/ẩn bảng Tài liệu — chỉ **một khe**, DD-05); bấm `[m]` khác khi đang mở → **thay nội dung** (không đóng/mở lại); "← Tài liệu" quay về bảng Tài liệu; ✕ / `Esc` đóng và **trả focus về phần tử đã mở bảng** (chip hoặc dòng nguồn).
- Khi mở: đưa focus vào tiêu đề bảng (`tabindex=-1`), thông báo qua `aria-live`: "Đã mở {tên}, trang {p}".

### 3.2 Dữ liệu
`GET /api/citations/{messageId}/{index}` (IF-07) → `{document_id, document_name, page, page_count, highlight}`; `410 DOCUMENT_DELETED` hoặc tài liệu không còn trong cache → trạng thái "Tài liệu đã bị xóa khỏi workspace" (`source.deleted`) kèm biểu tượng, không có nội dung PDF; lỗi mạng/5xx → thông báo `errors.generic` + nút "Thử lại".

### 3.3 Tải pdf.js và PDF
- `import('pdfjs-dist')` **động** (luật R-5); worker bằng `new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url)` — cùng origin, phù hợp CSP `worker-src 'self' blob:`.
- **Prefetch:** khi câu trả lời đầu tiên có trích dẫn kết thúc (T16 phát cờ), gọi `requestIdleCallback` để nạp trước chunk pdf.js + worker (không tải PDF).
- `getDocument({url: '/api/documents/{id}/file', withCredentials: true, rangeChunkSize: 65536, disableAutoFetch: true, disableStream: false})` — tải theo byte-range, chỉ lấy phần cần cho trang cần vẽ. Tái sử dụng `PDFDocumentProxy` khi bấm chip khác cùng tài liệu; **hủy** (`destroy()`) khi đóng bảng hoặc đổi tài liệu; giữ tối đa 3 trang đã vẽ trong bộ nhớ.
- Trạng thái tải: skeleton nếu chờ > 300 ms.

### 3.4 Vẽ trang
- `page.getViewport` với chiều rộng = chiều rộng nội dung bảng (co giãn khi đổi kích thước, debounce 150 ms); canvas nhân với `devicePixelRatio`; bên trên canvas là **lớp văn bản** (`TextLayer` của pdf.js) trong suốt để chọn/sao chép văn bản.
- Điều hướng: nút ‹ ›, phím `←`/`→` khi bảng có focus; hiển thị "Trang {p} / {N}" và nhãn "· trang được trích dẫn" khi `p` là trang trích.

### 3.5 Làm nổi bật `snippet` (thuật toán)
1. Sau khi lớp văn bản dựng xong, nối các `textContent` của các `<span>` thành một chuỗi `T` và giữ bảng ánh xạ vị trí → `span` + offset.
2. Chuẩn hóa **cả hai phía** giống nhau để so khớp: Unicode NFC, thay mọi chuỗi khoảng trắng/ngắt dòng bằng một dấu cách, loại dấu gạch nối cuối dòng (`-\n`), bỏ ký tự điều khiển; giữ nguyên chữ hoa/thường và dấu tiếng Việt.
3. Tìm `snippet` đã chuẩn hóa trong `T` đã chuẩn hóa: thử lần lượt **120 → 60 → 30** ký tự đầu của snippet (`indexOf`); nếu thất bại, thử tìm theo vị trí gợi ý `page_text_start` ± 400 ký tự bằng cách so khớp 30 ký tự đầu đoạn đó; nếu vẫn thất bại → **không làm nổi bật**.
4. Vùng khớp mở rộng đến hết `snippet` (hoặc ≤ 300 ký tự) rồi ánh xạ ngược sang các `span`; dùng `Range.getClientRects()` để vẽ các hình chữ nhật nền `--color-highlight` (`#FDE68A`, chữ vẫn là `--color-text`) nằm **sau** văn bản; không sửa DOM của lớp văn bản ngoài các lớp phủ.
5. `scrollIntoView({block:'center'})` cho vùng nổi bật đầu tiên.
6. Nếu không làm nổi bật được: giữ nguyên trang, hiện dòng ⓘ "Không xác định được vị trí chính xác — hiển thị trang được trích dẫn." (không coi là lỗi; ghi nhận số lần để đánh giá OQ-UX-01).

### 3.6 Analytics
**EV-030** `citation_clicked {question_id, document_id, page, citation_index, viewer_load_ms}` — `viewer_load_ms` đo từ lúc bấm đến khi trang đã vẽ xong; gửi qua `shared/lib/analytics`.

## 4. Tệp sẽ tạo
`features/source-viewer/{SourceViewerPanel.vue, usePdfDocument.ts, PdfPage.vue, highlight.ts, normalizeText.ts, useCitation.ts}`, `app/citationBus.ts`, mở rộng `RightPanel` (khe `source`), `*.test.ts`, fixture PDF nhỏ nhiều trang.

## 5. Tiêu chí hoàn thành
- [ ] AC-030.1: bấm `[1]` → bảng mở, trang đúng, đoạn nổi bật xuất hiện và được cuộn vào giữa; **thời gian từ bấm đến vẽ xong ≤ 1,5 giây** trên PDF 100 trang qua mạng nội bộ (đo bằng EV-030 `viewer_load_ms` và E2E; kèm số liệu trong PR).
- [ ] AC-030.2: trên bộ **10 trích dẫn mẫu** (PDF thật, gồm tiếng Việt có dấu, văn bản xuống dòng giữa câu, dấu gạch nối cuối dòng), đoạn nổi bật chứa thông tin đã trích ở **≥ 9/10** trường hợp; ghi kết quả vào PR.
- [ ] AC-030.3: tài liệu đã xóa → hiển thị "Tài liệu đã bị xóa khỏi workspace" thay nội dung; không lỗi, không treo.
- [ ] Thuật toán highlight: test đơn vị với văn bản mẫu — khác biệt khoảng trắng, xuống dòng giữa câu, gạch nối cuối dòng, dấu tiếng Việt (NFC/NFD khác nhau), `snippet` không có trong trang (→ không highlight, có ghi chú), `snippet` xuất hiện hai lần (→ chọn lần gần vị trí gợi ý).
- [ ] Bấm `[2]` khi đang mở `[1]` → thay nội dung mà không nhấp nháy đóng/mở; cùng tài liệu → không tải lại PDF.
- [ ] `Esc`/✕ đóng và trả focus đúng chip; "← Tài liệu" quay về bảng Tài liệu; `←`/`→` đổi trang.
- [ ] `pdfjs-dist` **không** nằm trong bundle khởi tạo (kiểm tra `size-limit` và phân tích bundle); prefetch chạy ở thời gian rảnh.
- [ ] `destroy()` được gọi khi đóng; bộ nhớ không tăng sau 20 lần mở/đóng liên tiếp (kiểm tra thủ công/Performance).
- [ ] Mạng lỗi khi tải PDF → thông báo + "Thử lại"; đóng/mở lại không để lại trạng thái hỏng.
- [ ] Bảng dùng được bằng bàn phím; axe-core 0 lỗi serious/critical; trang PDF có `aria-label` "Nội dung trang {p}"; hiển thị đúng dấu tiếng Việt; khớp prototype.
- [ ] EV-030 được gửi đúng một lần mỗi lần mở, không chứa nội dung.

## 6. Kiểm thử
Vitest (thuật toán highlight, ánh xạ vị trí); Vue Test Utils/Testing Library + MSW (trạng thái 410, lỗi mạng, thay đổi trích dẫn); Playwright (T21) đo thời gian mở và kiểm tra highlight trên PDF thật.

## 7. Ghi chú
Nếu OQ-UX-01 xác nhận backend có thể trả tọa độ chữ chính xác theo pdf.js, có thể thay bước 3.5 bằng vẽ trực tiếp theo tọa độ — giữ nguyên giao diện `highlight.ts` để thay thế dễ.
