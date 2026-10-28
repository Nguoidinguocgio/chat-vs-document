# TC-CHAT-08 — Ô nhập: giới hạn 2.000 ký tự, rỗng, Enter / Shift+Enter, bộ gõ tiếng Việt

| | |
|---|---|
| **Mã test case** | `TC-CHAT-08` |
| **Nhóm** | BB-02 Hỏi đáp có trích nguồn |
| **Ưu tiên** | P0 |
| **Truy vết** | ST-04 · AC-020.3 · PRD §5.2 |
| **Mã trong test plan** | BB-02 T8 · E-17 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=answered |
| **Thư mục kết quả** | `./test_report/TC-CHAT-08/` |
| **Spec tự động (dự kiến)** | `e2e/tests/chat/TC-CHAT-08.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Composer chặn câu hỏi rỗng / quá dài đúng cách và xử lý phím đúng, kể cả khi dùng bộ gõ tiếng Việt (IME).

## 2. Điều kiện tiên quyết

- `cd web; npm run dev:mock` → http://localhost:5173

## 3. Dữ liệu kiểm thử

**URL:** tất cả đều là `localhost` — giao diện http://localhost:5173, API http://localhost:8080.

**Tài khoản mock** (mật khẩu chung `Matkhau-2026`):

| Email | Mật khẩu | Ghi chú |
|---|---|---|
| `lan@congty.vn` | `Matkhau-2026` | Member — vào thẳng workspace |
| `admin@congty.vn` | `Matkhau-2026` | Admin |
| `moi@congty.vn` | `Matkhau-2026` | Mật khẩu tạm → bắt buộc đổi mật khẩu |
| `khoa@congty.vn` | `bất kỳ` | Bị khóa tạm (423) |
| `vohieu@congty.vn` | `Matkhau-2026` | Tài khoản bị vô hiệu hóa |

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-CHAT-08/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` | Ô nhập mở khóa; nút Gửi vô hiệu hóa khi ô rỗng | `01-empty-disabled.png` |
| 2 | Gõ 2.001 ký tự | Bộ đếm đỏ; hiện "Câu hỏi tối đa 2.000 ký tự"; Gửi vô hiệu hóa | `02-over-limit.png` |
| 3 | Xóa bớt còn 2.000 ký tự | Bộ đếm hết đỏ; Gửi dùng được | `03-at-limit.png` |
| 4 | Nhập "abc", bấm Shift+Enter, nhập "def" | Xuống dòng, **chưa** gửi | `04-shift-enter.png` |
| 5 | Bấm Enter | Câu hỏi được gửi | `05-enter-sends.png` |
| 6 | Dùng bộ gõ tiếng Việt (Telex): gõ "Dduwowcj" rồi Enter khi đang ở trạng thái soạn dở | Enter dùng để chốt chữ, **không** gửi sớm | `06-ime-no-early-send.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Giới hạn đúng 2.000 ký tự (2.000 hợp lệ, 2.001 bị chặn).
- [ ] Enter gửi, Shift+Enter xuống dòng.
- [ ] Không gửi sớm khi đang dùng IME.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-CHAT-08/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-CHAT-08/screenshots", "test_report/TC-CHAT-08/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-CHAT-08"; $env:REPORT_DIR = "test_report/TC-CHAT-08"
  npx playwright test e2e/tests/chat/TC-CHAT-08.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-CHAT-08/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 6 ảnh, một ảnh cho mỗi bước ở mục 4: `01-empty-disabled.png`, `02-over-limit.png`, `03-at-limit.png`, `04-shift-enter.png`, `05-enter-sends.png`, `06-ime-no-early-send.png` |
| `video/TC-CHAT-08.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-CHAT-08.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 6 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-CHAT-08` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
