# T21 — QA: E2E Playwright và khả năng tiếp cận

| | |
|---|---|
| **Giai đoạn / Loại** | 4 — Chất lượng / QA |
| **Ước tính** | 4 ngày (khung và các kịch bản P0 có thể bắt đầu sớm từ GĐ 1, hoàn thiện ở GĐ 4) |
| **Phụ thuộc** | T19, T18 (kịch bản đầy đủ); khung chạy được từ T10/T11 |
| **Mở khóa** | T23 |
| **Liên quan** | G-01, G-09; US-010 – US-012, US-020 – US-024, US-026, US-030; NFR-U01, NFR-U03; [kịch bản kiểm thử BB-01/BB-02](../user_stories/US_Backbone_Upload_va_HoiDap.md) |

## 1. Mục tiêu
Chứng minh bằng test tự động rằng **mọi luồng P0 của hai story xương sống chạy đúng trên Chrome và Edge**, và giao diện dùng được bằng bàn phím và trình đọc màn hình. Là bằng chứng cho G-01 và G-09.

## 2. Phạm vi
**Trong:** cấu hình Playwright, môi trường xác định (backend thật + `FakeLLM`/`FakeEmbedding`), bộ file PDF mẫu, kịch bản E2E, kiểm thử a11y bằng axe-core, danh sách kiểm tra thủ công NVDA, tích hợp CI.
**Ngoài:** thử tải (T22), đánh giá chất lượng AI (T20 — E2E không kiểm chứng nội dung thật của LLM).

## 3. Đặc tả chi tiết

### 3.1 Môi trường
- `e2e/playwright.config.ts`: dự án `chromium` và `msedge` (channel `msedge`), viewport 1440×900 (thêm 1100×800 cho một số kịch bản), `trace: 'retain-on-failure'`, `video: 'retain-on-failure'`, thử lại 1 lần trên CI.
- **Môi trường xác định:** `docker compose -f ops/docker-compose.e2e.yml up` (hoặc `ops/scripts/e2e-up.ps1` khi dùng PostgreSQL cài sẵn) chạy PostgreSQL + API (Spring profile `e2e`) (`LLM_PROVIDER=fake`, `EMBEDDING_PROVIDER=fake`, `STREAM_STALL_SECONDS=3`, `LLM_FIRST_TOKEN_TIMEOUT_S=2` để kịch bản "chậm" chạy nhanh) + web build. Mỗi test file khởi tạo dữ liệu qua API/CLI (tạo người dùng, xóa tài liệu) — không phụ thuộc thứ tự test.
- Người dùng mẫu: `lan` (Member), `minh` (Member, ngữ cảnh thứ hai), `tam` (mật khẩu tạm — để test đổi mật khẩu).
- **Fixtures PDF** (`e2e/fixtures/`): sinh bằng mã (dùng lại `MakeFixtures.java` trong `api/src/test/java` của T08, chạy qua `./mvnw exec:java` hoặc xuất sẵn vào `e2e/fixtures/`): `ok_vi.pdf`, `ok_en.pdf`, `too_many_101p.pdf`, `encrypted.pdf`, `scanned.pdf`, `mixed_scan.pdf`, `corrupted.pdf`, `fake_docx.pdf` (đuôi pdf nhưng nội dung không phải PDF), `big_11mb.pdf`, và `injection.pdf`.

### 3.2 Kịch bản bắt buộc (ánh xạ tới kịch bản T1… của tài liệu user stories)
| Mã | Kịch bản | Kiểm tra chính | Từ |
|---|---|---|---|
| E-01 | Đăng nhập đúng/sai/khóa sau 5 lần/mật khẩu tạm bắt đổi | AC-001.1 – 001.4 | T04, T05 |
| E-02 | Hết phiên khi đang soạn → đăng nhập lại còn nháp | AC-001.5 | T05, T15 |
| E-03 | Truy cập `/c` khi chưa đăng nhập | AC-001.6 | T05 |
| E-04 | BB-01 T1: workspace trống → thả 1 PDF → Đang xử lý → Sẵn sàng + toast + ô nhập mở | AC-010.1, AC-020.4 | T10, T11 |
| E-05 | BB-01 T2: thả 5 file trong đó `corrupted.pdf` | AC-010.3, AC-012.6 | T10 |
| E-06 | BB-01 T3/T4/T6: `fake_docx.pdf`, `big_11mb.pdf` (chặn ở client, **không có request mạng**), `too_many_101p`, `encrypted`, `scanned` | AC-012.1 – 012.5 đúng nguyên văn | T10, T11 |
| E-07 | BB-01 T7: `mixed_scan.pdf` → Sẵn sàng kèm cảnh báo trang | AC-012.8 | T11 |
| E-08 | BB-01 T5: có 18 tài liệu, chọn 4 file | AC-010.5 | T10 |
| E-09 | BB-01 T8: hai ngữ cảnh trình duyệt; A tải lên, B thấy trong ≤ 5 giây | AC-011.2 | T11 |
| E-10 | BB-01 T9: `context.setOffline(true)` khi tải → "Gián đoạn", thử lại, rồi nút "Thử lại" | PRD §5.2 | T10 |
| E-11 | Cạnh tranh chỗ trống: hai ngữ cảnh cùng tải khi còn 1 chỗ | workspace không vượt 20 | T07, T10 |
| E-12 | BB-02 T1: hỏi "thanh toán" → streaming → `[1][2]` + nguồn | AC-020.1, AC-022.1, AC-021.2/3 | T16 |
| E-13 | BB-02: bấm `[1]` → bảng nguồn mở ≤ 1,5 s, đoạn nổi bật, `Esc` trả focus | AC-030.1 | T18 |
| E-14 | BB-02 T4/T5: "không tìm thấy" và "một phần" | AC-023.1/023.3 | T16 |
| E-15 | BB-02 T6: hỏi tiếng Việt, trả lời tiếng Việt | AC-024.1 | T13 |
| E-16 | BB-02 T7: `injection.pdf` + từ khóa `injection` → không có script/ảnh/liên kết trong DOM; chip chỉ cho `[n]` hợp lệ | NFR-S05 | T13, T16 |
| E-17 | BB-02 T8: 2.001 ký tự / rỗng → Gửi khóa; gõ tiếng Việt bằng bộ gõ không gửi sớm | ST-04, IME | T15 |
| E-18 | BB-02 T9: Dừng khi đang trả lời → "Đã dừng", Tạo lại | US-026 | T19 |
| E-19 | BB-02 T10: `ngat` (gián đoạn), `cham` (quá hạn), `loi dich vu` | PRD §5.2 | T19 |
| E-20 | Xóa tài liệu bằng API rồi bấm trích dẫn cũ → "Tài liệu đã bị xóa khỏi workspace" | AC-030.3 | T18 |
| E-21 | Vô hiệu hóa người dùng bằng CLI khi đang mở → về `/login` ≤ 60 s | AC-003.1 | T04, T11 |
| E-22 | Hội thoại của người khác: truy cập `/c/:id` → 404 | D-001, G-07 | T14 |

### 3.3 Khả năng tiếp cận
- **axe-core** (`@axe-core/playwright`) chạy ở **mọi trạng thái chính**: đăng nhập, đổi mật khẩu, workspace trống, đang tải lên, danh sách có lỗi, hỏi-đáp (đang tạo, có trích dẫn, không tìm thấy, một phần, lỗi, gián đoạn), bảng Xem nguồn, hộp thoại, trang 403/404. Ngưỡng: **0 vi phạm mức serious/critical** (vi phạm moderate được ghi nhận và xử lý theo thỏa thuận).
- **Chỉ dùng bàn phím** (Playwright không dùng chuột): đăng nhập → tải lên (qua nút "Chọn file") → hỏi → mở trích dẫn → đóng bảng bằng `Esc` → hội thoại mới bằng `Ctrl+Shift+O`; kiểm tra thứ tự focus và vòng focus hiển thị (ảnh chụp so sánh).
- **Tương phản & zoom:** kiểm tra tỷ lệ tương phản tính toán cho các token; test zoom 200% (viewport 720×450 tương đương): không cuộn ngang, không mất chức năng.
- **`prefers-reduced-motion`:** bật qua `page.emulateMedia`, kiểm tra spinner/con trỏ không hoạt ảnh.
- **Thủ công (NVDA + Chrome), biên bản `e2e/a11y-manual.md`:** đọc đúng thông báo "Đang tạo câu trả lời" / "Câu trả lời đã xong, có n trích dẫn"; không đọc từng token; chip trích dẫn đọc "Trích dẫn n: tên, trang p"; trạng thái tài liệu được đọc khi đổi; hộp thoại bẫy focus; bảng nguồn nhận focus khi mở.

### 3.4 CI
Job `e2e` chạy trên PR vào `dev/phase1`/`main`: dựng môi trường, chạy bộ E-xx trên Chrome và Edge (Edge có thể chạy theo lịch hằng đêm nếu quá chậm — nhưng **bắt buộc xanh trước launch**), tải lên artifact (trace/video/báo cáo HTML). Mục tiêu tổng thời gian ≤ 10 phút cho bộ P0 trên một trình duyệt.

## 4. Tệp sẽ tạo
`e2e/playwright.config.ts`, `e2e/tests/*.spec.ts` (nhóm theo `auth`, `upload`, `chat`, `source`, `errors`, `a11y`), `e2e/fixtures/*`, `e2e/helpers/{api,seed,sse}.ts`, `ops/docker-compose.e2e.yml`, `e2e/a11y-manual.md`, bước CI.

## 5. Tiêu chí hoàn thành
- [ ] Toàn bộ E-01 … E-22 chạy xanh trên **Chrome và Edge**, ổn định (không flaky: chạy 5 lần liên tiếp đều xanh).
- [ ] Mỗi kịch bản xác minh đúng **nguyên văn thông điệp** theo PRD/SDD (so khớp chuỗi, không "chứa từ khóa").
- [ ] Kịch bản E-06 chứng minh file > 10 MB/không phải PDF **không** sinh request tải lên (kiểm tra bằng `page.on('request')`).
- [ ] E-09/E-11 dùng hai ngữ cảnh trình duyệt độc lập và xanh ổn định.
- [ ] axe-core: 0 vi phạm serious/critical ở mọi trạng thái trong 3.3; báo cáo đính kèm.
- [ ] Luồng chỉ dùng bàn phím hoàn tất; vòng focus nhìn thấy được ở mọi phần tử tương tác.
- [ ] Biên bản NVDA thủ công hoàn tất, các lỗi được ghi vào bug tracker; không còn lỗi chặn.
- [ ] Bộ E2E P0 chạy trong CI ≤ 10 phút (một trình duyệt); artifact được lưu khi lỗi.
- [ ] Báo cáo tổng hợp "mọi AC P0 → test E-xx" (bảng truy vết) đính kèm — là bằng chứng G-01.

## 6. Kiểm thử
Chính các test E2E; chạy lặp chống flaky; xem lại video/trace của lần lỗi đầu tiên để loại bỏ `sleep` cố định (dùng chờ theo điều kiện).

## 7. Ghi chú
Không dùng LLM thật trong E2E (không xác định, tốn tiền). Chất lượng nội dung thật được đo ở T20. Để E2E ổn định, tránh `waitForTimeout`; dùng `expect.poll`/`toBeVisible` với thời hạn rõ.
