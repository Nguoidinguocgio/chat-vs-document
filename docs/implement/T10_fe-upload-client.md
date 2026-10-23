# T10 — Frontend: kiểm tra file, kéo-thả, hàng đợi tải lên

| | |
|---|---|
| **Giai đoạn / Loại** | 1 — BB-01 Tải lên PDF / Frontend |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T05, T06 (dùng MSW) |
| **Mở khóa** | T11 hiển thị kết quả; kiểm thử E2E (T21) |
| **Liên quan** | US-010 (AC-010.1 – 010.5), US-012 (AC-012.1, 012.2); SDD ALG-02, IS-03, ST-01, DD-04, DD-09; prototype SCR-03 (`handleFiles`, `startUpload`) |

## 1. Mục tiêu
Người dùng thả hoặc chọn một/nhiều PDF và thấy ngay: file nào được nhận, file nào bị từ chối kèm lý do, tiến trình riêng từng file, tự thử lại khi mất mạng — mà không cần tải lại trang.

## 2. Phạm vi
**Trong:** hàm kiểm tra file, vùng kéo-thả, lớp phủ toàn màn hình, hàng đợi tải lên, danh sách từ chối, phản hồi lỗi máy chủ.
**Ngoài:** hiển thị danh sách tài liệu và trạng thái xử lý (T11); cảnh báo trùng file (US-015).

## 3. Đặc tả chi tiết

### 3.1 `validateFiles(files, existingCount)` — hàm thuần, không phụ thuộc UI (ALG-02)
Trả `{accepted: File[], rejected: {file, messageKey}[]}`. Duyệt **theo thứ tự chọn**, `slots = 20 − existingCount` (`existingCount` = tổng tài liệu hiện có, **gồm đang xử lý và lỗi**):
1. Đuôi không phải `.pdf` hoặc 5 byte đầu (`await file.slice(0,5).text()`) khác `%PDF-` → `documents.not_pdf`.
2. `file.size > 10 * 1024 * 1024` → `documents.too_large`.
3. `slots <= 0` → `documents.limit_reached`; ngược lại nhận file và `slots -= 1`.
Ví dụ: còn 2 chỗ, chọn 4 file hợp lệ → nhận 2 file đầu, từ chối 2 file sau (AC-010.5). Một file không hợp lệ **không** chiếm chỗ. Máy chủ vẫn kiểm tra lại mọi thứ.

### 3.2 Vùng kéo-thả và lớp phủ
- `UploadDropzone` (trong bảng Tài liệu — tích hợp ở T11): vùng đứt nét + nút "Chọn file" (input `type=file multiple accept="application/pdf,.pdf"`), dòng giới hạn "≤ 10 MB · ≤ 100 trang · PDF có văn bản". Khi đủ 20 tài liệu: vùng bị khóa, hiển thị `documents.limit_reached`.
- `GlobalDropOverlay`: lắng nghe `dragenter/dragleave/dragover/drop` trên `window`; chỉ hiện khi `dataTransfer.types` có `Files`; dùng bộ đếm `dragenter/leave` để không nhấp nháy; thả file bất kỳ đâu → gọi cùng luồng với nút chọn file; mở bảng Tài liệu nếu đang đóng; `preventDefault` để trình duyệt không mở file.
- Tiếp cận: nút "Chọn file" thao tác được bằng bàn phím; lớp phủ có `aria-hidden` và không bẫy focus.

### 3.3 Hàng đợi tải lên (`features/documents/upload/queue.ts`, Zustand)
- Mỗi file: `UploadTask {localId, file, progress: 0–100, phase: 'queued'|'uploading'|'interrupted', attempts}`.
- **Tối đa 2 task `uploading` đồng thời** (DD-09); còn lại `queued`.
- Dùng `XMLHttpRequest` (cho `upload.onprogress`) gửi `POST /api/documents` multipart, trường `file`, header `X-Requested-With: fetch` (thư mục này được lint cho phép dùng XHR). Có thể tính SHA-256 bằng Web Crypto và gửi `X-Content-SHA256` (tùy chọn, bỏ qua nếu lỗi).
- **Lỗi mạng** (XHR `onerror`/`ontimeout`): tự thử lại tối đa **3 lần** với độ trễ 1 s, 2 s, 4 s; trong lúc đó hiển thị "Gián đoạn — đang thử lại (k/3)". Hết lần → `phase='interrupted'` + nút "Thử lại" (đặt `attempts=0`, xếp lại hàng) và nút "Hủy". Lỗi HTTP 4xx **không** thử lại.
- **Ánh xạ phản hồi:** `201` → bỏ task khỏi hàng đợi và **chèn `Document` vào cache** danh sách tài liệu (tránh nhấp nháy, trạng thái `processing`); `400 NOT_PDF`, `413`, `409 DOCUMENT_LIMIT_REACHED` → chuyển vào danh sách từ chối với khóa thông điệp tương ứng; `401/403` → xử lý toàn cục của `shared/api`; lỗi khác → `errors.generic` kèm "Thử lại".
- Một file lỗi **không làm dừng** các file còn lại (AC-010.3).
- Đóng tab giữa chừng: task mất; file chưa tải xong không xuất hiện ở người khác (do chỉ máy chủ tạo bản ghi khi nhận đủ file).

### 3.4 Danh sách từ chối
`rejections` (Zustand): mỗi mục `{id, fileName, messageKey}` hiển thị thành khối đỏ có `role="alert"`, "tên file: thông điệp" và nút ✕ để bỏ; không tự biến mất; tên file hiển thị qua `midEllipsis`.

### 3.5 Analytics
Không gửi sự kiện từ client cho tải lên; EV-010/011/012/013 do máy chủ ghi (T07/T08). (Chỉ gửi header `X-Batch-Size` = số file nhận trong lần chọn để backend ghi `batch_size`.)

## 4. Tệp sẽ tạo
`features/documents/upload/{validateFiles.ts, queue.ts, UploadDropzone.tsx, GlobalDropOverlay.tsx, RejectionList.tsx, useUpload.ts}` + `*.test.ts(x)`; mở rộng `mocks/handlers.ts` cho `409/413/400`, lỗi mạng, tải chậm.

## 5. Tiêu chí hoàn thành
- [ ] `validateFiles`: test bảng gồm — `.docx`; `.pdf` giả (nội dung không có `%PDF-`); 10 MB và 10 MB + 1 byte; 18 tài liệu + 4 file; 20 tài liệu + 1 file; thứ tự chọn được giữ; file hỏng không chiếm chỗ.
- [ ] AC-010.3: chọn 5 file → mỗi file có tiến trình riêng; file thứ 3 trả lỗi (MSW) mà 4 file còn lại vẫn hoàn tất.
- [ ] Không bao giờ quá 2 request tải lên chạy song song (test đếm).
- [ ] AC-010.4: khi đã đủ 20, chọn thêm → không có request mạng nào được gửi (test spy), hiển thị `documents.limit_reached`.
- [ ] AC-010.5: 18 tài liệu + 4 file → đúng 2 request được gửi, 2 mục từ chối với `limit_reached`.
- [ ] Mất mạng giả lập → thấy "Gián đoạn", tự thử lại đúng 3 lần theo 1 s/2 s/4 s (đồng hồ giả), rồi hiện "Thử lại"; bấm "Thử lại" tải lại thành công.
- [ ] Phản hồi `409 DOCUMENT_LIMIT_REACHED` từ máy chủ (cạnh tranh) hiển thị đúng thông báo và không tạo "tài liệu ma" trong danh sách.
- [ ] Thả file bất kỳ đâu trên màn hình hoạt động; lớp phủ chỉ hiện khi kéo file (không hiện khi kéo văn bản/ảnh trong trang).
- [ ] Thông báo từ chối dùng đúng khóa i18n/câu chữ; thao tác dùng được bằng bàn phím; axe-core 0 lỗi serious/critical.
- [ ] Tên file dài/có dấu hiển thị rút gọn giữa, tooltip tên đầy đủ.

## 6. Kiểm thử
Vitest (hàm thuần, hàng đợi với XHR giả/đồng hồ giả); Testing Library + MSW cho vùng kéo-thả; ghi chú các kịch bản để T21 chạy lại bằng Playwright với file thật.

## 7. Ghi chú
Nếu đọc `file.slice(0,5).text()` lỗi (file bị chặn đọc), coi như không phải PDF hợp lệ và để máy chủ quyết định — không chặn ở client.
