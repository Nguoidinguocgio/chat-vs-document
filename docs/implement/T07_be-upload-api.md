# T07 — Backend: API tải lên tài liệu

| | |
|---|---|
| **Giai đoạn / Loại** | 1 — BB-01 Tải lên PDF / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T03, T04 |
| **Mở khóa** | T08, T09 |
| **Liên quan** | US-010 (AC-010.1 – 010.5), US-012 (AC-012.1, 012.2, 012.7); D-001, D-002; RSK-002; EV-010, EV-011 |

## 1. Mục tiêu
Nhận file PDF, kiểm tra chặt, **không bao giờ vượt 20 tài liệu** dù nhiều người tải cùng lúc, lưu an toàn và giao cho pipeline xử lý (T08) chạy nền.

## 2. Phạm vi
**Trong:** `POST /api/documents`, `POST /api/documents/{id}/retry`, kiểm tra file, giữ chỗ nguyên tử, lưu file, hàng đợi xử lý nền, nhật ký kiểm toán, analytics.
**Ngoài:** nội dung pipeline xử lý (T08); danh sách và SSE (T09); xóa, trùng file, tóm tắt (làm sau). Cột `sha256` vẫn được lưu để phục vụ tính năng phát hiện trùng (US-015) sau này.

## 3. Đặc tả chi tiết

### 3.1 `POST /api/documents`
`multipart/form-data`, một trường `file`. Header tùy chọn `X-Content-SHA256` (bỏ qua nếu thiếu; không tin giá trị client — luôn tự tính). Chỉ người đã đăng nhập (mọi vai trò).

Thứ tự xử lý (dừng ở lỗi đầu tiên):
1. **Tên file:** lấy tên gốc, bỏ ký tự điều khiển và dấu phân cách đường dẫn, cắt ≤ 255 ký tự (giữ nguyên chữ có dấu); tên dùng để hiển thị — **không** dùng làm đường dẫn lưu.
2. **Đuôi** không phải `.pdf` (không phân biệt hoa thường) → `400 NOT_PDF`.
3. **Đọc luồng** vào file tạm, đếm byte; vượt `MAX_FILE_MB` (10 MB) → hủy đọc, `413 FILE_TOO_LARGE` (không đọc hết file).
4. **Nội dung:** 5 byte đầu phải là `%PDF-` (không tin `Content-Type`) → nếu không, `400 NOT_PDF`.
5. **Giữ chỗ nguyên tử:** mở giao dịch, `SELECT pg_advisory_xact_lock(<hằng số workspace>)`, đếm tài liệu `deleted_at IS NULL` (**mọi trạng thái**: đang xử lý, sẵn sàng, lỗi). Nếu `≥ MAX_DOCS (20)` → `409 DOCUMENT_LIMIT_REACHED` (`message_key=documents.limit_reached`, `details.limit=20`). Ngược lại chèn bản ghi `documents` (`status=processing`) và commit **trước khi** nhả khóa. Nhờ vậy hai yêu cầu song song khi còn 1 chỗ: đúng một thành công, một bị từ chối.
6. **Lưu file** qua `StoragePort.put("documents/{uuid}.pdf", …)`; tự tính SHA-256 và `size_bytes`. Nếu lưu thất bại: xóa bản ghi vừa chèn, trả `500`.
7. **Xếp hàng xử lý** (3.3), phát sự kiện nội bộ `document.created` (T09 sẽ chuyển thành SSE), ghi `audit_logs` (`action=document.upload`, `target=<id>`, `result=success`) và analytics **EV-010** (`file_size_bytes`, `batch_size` lấy từ header tùy chọn `X-Batch-Size`).
8. Trả `201` `Document` (`status=processing`, `page_count=null`).

Mọi từ chối (400/409/413) ghi analytics **EV-011** với `reason` ∈ `not_pdf | too_large | limit_reached` và audit `result=failure`.

### 3.2 `POST /api/documents/{id}/retry`
Chỉ khi `status=failed` **và** `error_code=SYSTEM_ERROR`; ngược lại `409 NOT_RETRYABLE`. Đặt lại `status=processing`, xóa `error_code`, xếp hàng xử lý lại. Trả `202` `Document`. Mọi vai trò đã đăng nhập được thử lại tài liệu của workspace.

### 3.3 Hàng đợi xử lý nền
MVP không cần Redis: dùng hàng đợi trong tiến trình (`asyncio.Queue` + N worker, mặc định N = 2; giới hạn CPU cho trích xuất). Bảng `documents` là nguồn sự thật:
- Khi khởi động, tìm tài liệu `status=processing` và `uploaded_at` cũ hơn 5 phút (đã mồ côi do khởi động lại) → xếp hàng lại.
- Giao diện `JobQueue.enqueue(document_id)` được tách riêng để sau này thay bằng hàng đợi ngoài mà không sửa nghiệp vụ.

### 3.4 Bảo mật
Chỉ lưu tệp ở `STORAGE_DIR` bằng khóa là UUID (chống path traversal); tên file người dùng không bao giờ ghép vào đường dẫn. Giới hạn kích thước body ở tầng ASGI/proxy ở mức hơi lớn hơn 10 MB để từ chối sớm. Không đọc toàn bộ file vào bộ nhớ (xử lý theo luồng).

## 4. Tệp sẽ tạo
`routers/documents.py` (phần ghi), `services/documents.py`, `services/job_queue.py`, `tests/test_upload_*.py`, fixtures PDF nhỏ.

## 5. Tiêu chí hoàn thành
- [ ] AC-010.1: PDF hợp lệ ≤ 10 MB → `201`, trạng thái `processing`, bản ghi và file đã lưu.
- [ ] AC-012.1: file `.docx` đổi đuôi `.pdf` → `400 NOT_PDF` (kiểm tra bằng nội dung); file `.txt` → `400 NOT_PDF`.
- [ ] AC-012.2: file 10 MB + 1 byte → `413 FILE_TOO_LARGE`, không lưu gì, không đọc hết body (đo bằng test luồng).
- [ ] AC-010.4: workspace đã có 20 tài liệu (gồm đang xử lý và lỗi) → `409 DOCUMENT_LIMIT_REACHED`, **không** lưu file.
- [ ] AC-010.5 (phần backend): có 18 tài liệu, gửi 4 yêu cầu tuần tự → 2 thành công, 2 bị `409`.
- [ ] **Cạnh tranh:** 5 yêu cầu song song khi còn 1 chỗ → đúng 1 `201`, 4 `409`; tổng tài liệu = 20 (chạy lặp ≥ 20 lần để bắt lỗi chạy đua).
- [ ] AC-012.7: `retry` chỉ hoạt động với `SYSTEM_ERROR`; lỗi khác → `409 NOT_RETRYABLE`.
- [ ] Tên file có dấu và ký tự đặc biệt (`Hợp đồng A (bản cuối).pdf`, `../../x.pdf`) được lưu/hiển thị an toàn; đường dẫn lưu không chứa tên người dùng.
- [ ] Tải lên và từ chối đều có bản ghi nhật ký kiểm toán và sự kiện EV-010/EV-011 đúng thuộc tính (không chứa tên file).
- [ ] Sau khi khởi động lại dịch vụ, tài liệu mồ côi ở `processing` được xếp hàng lại.
- [ ] Người chưa đăng nhập → `401`; thiếu `X-Requested-With` → `403`.

## 6. Kiểm thử
pytest với Postgres thật cho cạnh tranh (dùng `asyncio.gather`); fixtures: PDF hợp lệ nhỏ, file giả đuôi `.pdf`, file 10 MB + 1; test khởi động lại bằng cách tạo bản ghi `processing` cũ.

## 7. Ghi chú
Mã hóa khi lưu (AC-060.1) thực hiện ở mức đĩa/volume hoặc dịch vụ lưu trữ — xác nhận với CNTT; ghi lại bằng chứng cho An toàn thông tin.
