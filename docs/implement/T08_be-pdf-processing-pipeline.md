# T08 — Backend: pipeline xử lý PDF

| | |
|---|---|
| **Giai đoạn / Loại** | 1 — BB-01 Tải lên PDF / Backend |
| **Ước tính** | 5 ngày |
| **Phụ thuộc** | T07 |
| **Mở khóa** | T12 (truy hồi cần dữ liệu đã lập chỉ mục) |
| **Liên quan** | US-010 (AC-010.1/010.2), US-012 (AC-012.3 – 012.8); D-008 (không OCR); TH-03; RSK-003; EV-012, EV-013 |

## 1. Mục tiêu
Biến một PDF đã tải lên thành dữ liệu tìm kiếm được **kèm số trang chính xác**, và phát hiện đúng từng loại tài liệu không dùng được để người dùng không tưởng nhầm nội dung đã được tra cứu.

## 2. Phạm vi
**Trong:** mở/kiểm tra PDF, trích văn bản từng trang, phát hiện lỗi, cắt đoạn (chunk), tạo vector, lưu, cập nhật trạng thái, adapter embedding thật.
**Ngoài:** OCR (D-008); truy hồi (T12); bảng biểu/hình ảnh đặc biệt (chấp nhận hạn chế, RSK-003).

## 3. Đặc tả chi tiết

### 3.1 Các bước (phương thức `PdfPipeline.processDocument(documentId)`)
1. **Tải file** từ `StoragePort`; đặt thời hạn tối đa 120 giây cho cả pipeline.
2. **Mở PDF** bằng Apache PDFBox (`Loader.loadPDF`). Lỗi mở/đọc cấu trúc (`IOException`) → `CORRUPTED`.
3. **Mật khẩu:** `InvalidPasswordException` hoặc `document.isEncrypted()` mà không mở được bằng mật khẩu rỗng → `PASSWORD_PROTECTED`.
4. **Số trang:** `getNumberOfPages() > MAX_PAGES (100)` → `TOO_MANY_PAGES`; lưu `page_count` thật để thông báo "File có {n} trang…".
5. **Trích văn bản từng trang:** `PDFTextStripper` với `setStartPage(p)`/`setEndPage(p)` (`setSortByPosition(true)`); chuẩn hóa Unicode NFC (`java.text.Normalizer`), thay nhiều khoảng trắng/ngắt dòng liên tiếp bằng một dấu cách, **giữ** ngắt đoạn (dòng trống) làm một `\n\n`; bỏ ký tự điều khiển. Một trang là **không đọc được** nếu sau chuẩn hóa có `< MIN_PAGE_CHARS (20)` ký tự.
6. **Phân loại:**
   - **Mọi** trang không đọc được → `SCANNED_PDF` (không có lớp văn bản).
   - Một phần trang không đọc được → vẫn xử lý; `unreadable_pages` = danh sách số trang (bắt đầu từ 1) → kết quả "Sẵn sàng kèm cảnh báo" (AC-012.8).
7. **Lưu văn bản trang:** `document_pages(document_id, page, text)` (cả trang không đọc được, văn bản rỗng).
8. **Cắt đoạn (chunk):** mỗi trang tách riêng — **không bao giờ cắt qua ranh giới trang**.
   - Ưu tiên cắt theo đoạn (`\n\n`), rồi theo câu (`. `, `? `, `! `, `; `), cuối cùng theo khoảng trắng.
   - Kích thước mục tiêu 800 ký tự, tối đa 1.000; chồng lấn 100 ký tự giữa các đoạn liên tiếp **cùng trang**; đoạn ngắn hơn 80 ký tự được ghép vào đoạn kề.
   - Mỗi chunk lưu `page`, `char_start`, `char_end` (vị trí trong văn bản trang đã chuẩn hóa — dùng để làm nổi bật ở T17/T18) và `text`.
9. **Tạo vector** theo lô 32 đoạn qua `EmbeddingPort`; thử lại tối đa 3 lần (1 s, 2 s, 4 s) khi lỗi tạm thời; hết lần → `SYSTEM_ERROR`.
10. **Ghi nguyên tử:** trong một giao dịch: xóa chunk/trang cũ (nếu chạy lại), chèn trang + chunk, cập nhật `documents` (`page_count`, `unreadable_pages`, `status=ready`, `processed_at`, `error_code=null`). Lỗi giữa chừng → rollback, tài liệu về `failed` + mã lỗi tương ứng; **không bao giờ** để chunk nửa chừng với `status=ready`.
11. **Kết thúc:** phát sự kiện nội bộ `document.status_changed` (T09 chuyển thành SSE) và analytics:
    - Thành công: **EV-012** `{document_id, page_count, processing_ms, unreadable_page_count}`.
    - Thất bại: **EV-013** `{document_id, reason}` với `reason` ∈ `too_many_pages | password_protected | scanned_pdf | corrupted | system_error`.
    - Ghi nhật ký `document.process` (`success/failure`).

Lỗi không dự đoán được (exception khác, timeout) → `SYSTEM_ERROR` (ghi stack trace vào log, **không** ghi văn bản tài liệu).

### 3.2 Adapter embedding thật (`port/adapter/Embedding<Provider>Adapter.java`)
Hiện thực `EmbeddingPort` cho nhà cung cấp được chọn (việc #1 ở kế hoạch). **Yêu cầu bắt buộc:** mô hình **đa ngôn ngữ**, hỗ trợ tốt tiếng Việt và tiếng Anh và truy hồi chéo ngôn ngữ (AC-024.2); `EMBEDDING_DIM` khớp cột `chunks.embedding`; vector được chuẩn hóa độ dài 1 để dùng khoảng cách cosine. Có giới hạn kích thước lô và thử lại theo 3.1 bước 9. Nếu mô hình dùng tiền tố (ví dụ `query:`/`passage:`), đóng gói trong adapter qua hai phương thức `embedDocuments` / `embedQuery` (T12 gọi `embedQuery`).

### 3.3 Hiệu năng
Mục tiêu p90 ≤ 60 giây cho PDF 100 trang (TH-03) — đo **toàn bộ** thời gian từ lúc xếp hàng; ghi nhận riêng thời gian trích văn bản, tạo vector để dễ tối ưu. Trích văn bản chạy trên pool worker riêng của `JobQueue` (không chạy trên thread xử lý yêu cầu HTTP).

## 4. Tệp sẽ tạo
`service/PdfPipeline.java` (+ `Chunker.java`, `TextNormalizer.java`), `port/adapter/Embedding<Provider>Adapter.java`, `PdfPipelineIT.java`, `ChunkerTest.java`, `src/test/resources/pdfs/` (sinh bằng tiện ích `MakeFixtures.java` trong `src/test/java`, dùng PDFBox).

## 5. Tiêu chí hoàn thành
Fixtures sinh bằng mã (PDFBox, `MakeFixtures.java`): `ok_5p_vi.pdf` (5 trang tiếng Việt có dấu), `ok_en.pdf`, `too_many_101p.pdf`, `encrypted.pdf`, `scanned_images_only.pdf`, `mixed_3scan_10text.pdf`, `corrupted.bin` (đuôi `.pdf`).

- [ ] AC-012.3: `too_many_101p.pdf` → `failed`, `error_code=TOO_MANY_PAGES`, `error_details.page_count=101`.
- [ ] AC-012.4: `encrypted.pdf` → `PASSWORD_PROTECTED`.
- [ ] AC-012.5: `scanned_images_only.pdf` → `SCANNED_PDF`.
- [ ] AC-012.6: `corrupted.bin` → `CORRUPTED`.
- [ ] AC-012.7: embedding giả ném lỗi tạm thời liên tục → `SYSTEM_ERROR`; sau khi sửa và gọi `retry` (T07) → `ready`.
- [ ] AC-012.8: `mixed_3scan_10text.pdf` → `ready`, `unreadable_pages` đúng 3 số trang.
- [ ] AC-010.1: `ok_5p_vi.pdf` → `ready`; số chunk > 0; mỗi chunk có `page` đúng trang nguồn (kiểm tra bằng nội dung đánh dấu theo trang); không chunk nào cắt qua ranh giới trang.
- [ ] Văn bản tiếng Việt có dấu được giữ nguyên (NFC), không mất/vỡ dấu.
- [ ] `char_start/char_end` của mọi chunk thỏa `pageText.substring(charStart, charEnd)` khớp (sau chuẩn hóa) với `text` của chunk.
- [ ] Ghi nguyên tử: tiêm lỗi giữa bước chèn chunk → không có chunk nào được giữ, trạng thái `failed`.
- [ ] Chạy lại `processDocument` cho cùng tài liệu là idempotent (không nhân đôi chunk).
- [ ] Đo: PDF 100 trang tạo tự động xử lý xong ≤ 60 giây với embedding giả có độ trễ giả lập thực tế (báo cáo số liệu trong PR).
- [ ] EV-012/EV-013 được ghi đúng; log không chứa văn bản tài liệu.

## 6. Kiểm thử
JUnit 5 với `FakeEmbedding`; test đơn vị riêng cho `Chunker` (ranh giới trang, chồng lấn, đoạn ngắn, tiếng Việt); test tích hợp end-to-end qua `POST /documents` (T07) rồi chờ `ready`.

## 7. Ghi chú
Bảng biểu và bố cục nhiều cột có thể ra văn bản lộn xộn (RSK-003) — chấp nhận ở MVP, nhưng thử sớm với PDF thật của đội PTKD ngay khi xong task này và ghi nhận các trường hợp xấu để quyết định cải tiến.

**Ghi chú khi hiện thực (2026-10-27):**
- Fixtures sinh lúc chạy test bằng `support/MakeFixtures.java` (PDFBox, font DejaVu Sans cho tiếng Việt) thay vì commit file nhị phân.
- Đo TH-03 (máy dev, embedding giả trễ 150 ms/lô 32 đoạn): **PDF 100 trang xử lý trong ~1,8–4,1 s**. `processing_ms` của EV-012 đo từ lúc pipeline bắt đầu; log `document_processed` tách `extract_ms` / `embed_ms`.
- Tiêm lỗi ghi nguyên tử: embedding trả sai số chiều từ lô 2 → INSERT chunk thất bại giữa chừng → rollback toàn bộ, `failed/SYSTEM_ERROR`, 0 trang và 0 chunk được giữ.
- Trang không đọc được: < 20 ký tự khác khoảng trắng. Văn bản trang lưu sau chuẩn hóa nên `char_start/char_end` khớp `substring` chính xác.
- Adapter thật `OllamaEmbeddingAdapter` (bge-m3 không cần tiền tố; `embedQuery` = `embed` một phần tử).
