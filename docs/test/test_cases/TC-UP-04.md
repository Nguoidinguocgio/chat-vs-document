# TC-UP-04 — File 11 MB bị chặn ở client, không gửi yêu cầu mạng

| | |
|---|---|
| **Mã test case** | `TC-UP-04` |
| **Nhóm** | BB-01 Tải lên PDF |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-012.2 · ALG-02 |
| **Mã trong test plan** | BB-01 T4 · E-06 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=answered |
| **Thư mục kết quả** | `./test_report/TC-UP-04/` |
| **Spec tự động (dự kiến)** | `e2e/tests/upload/TC-UP-04.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

File vượt 10 MB bị chặn phía client và **không phát sinh request tải lên**.

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

**Dữ liệu riêng của test case:**

- `big_11mb.pdf` — 11 MB, 5 byte đầu `%PDF-`

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-UP-04/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` | Workspace hiển thị | `01-workspace.png` |
| 2 | Chọn `big_11mb.pdf` (bắt đầu `%PDF-`, dung lượng 11 MB) | Hiển thị "File vượt quá 10 MB." | `02-rejected.png` |
| 3 | Kiểm tra Network / `page.on("request")` | Không có `POST /api/documents` | `03-no-request.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Thông báo đúng nguyên văn "File vượt quá 10 MB.".
- [ ] 0 yêu cầu tải lên được gửi (kiểm bằng `page.on("request")`).
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-UP-04/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-UP-04/screenshots", "test_report/TC-UP-04/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-UP-04"; $env:REPORT_DIR = "test_report/TC-UP-04"
  npx playwright test e2e/tests/upload/TC-UP-04.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-UP-04/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 3 ảnh, một ảnh cho mỗi bước ở mục 4: `01-workspace.png`, `02-rejected.png`, `03-no-request.png` |
| `video/TC-UP-04.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-UP-04.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 3 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-UP-04` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- Máy chủ vẫn kiểm tra lại mọi ràng buộc (ALG-02: client chỉ để phản hồi nhanh); phần kiểm tra phía máy chủ thuộc `TC-SEC-07`.
