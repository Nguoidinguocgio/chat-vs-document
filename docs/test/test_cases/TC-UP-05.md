# TC-UP-05 — Giới hạn 20 tài liệu: nhận theo thứ tự chọn, từ chối phần dư

| | |
|---|---|
| **Mã test case** | `TC-UP-05` |
| **Nhóm** | BB-01 Tải lên PDF |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-010.4 · AC-010.5 · AC-011.3 |
| **Mã trong test plan** | BB-01 T5 · E-08 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=near_limit |
| **Thư mục kết quả** | `./test_report/TC-UP-05/` |
| **Spec tự động (dự kiến)** | `e2e/tests/upload/TC-UP-05.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Khi workspace gần đầy, chỉ nhận số file còn chỗ theo thứ tự chọn; khi đủ 20, vùng kéo-thả bị khóa và mọi file bị từ chối mà không gửi lên máy chủ.

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

- `a.pdf`, `b.pdf`, `c.pdf`, `d.pdf` — PDF nhỏ hợp lệ

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-UP-05/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` | Workspace có 19 tài liệu; bộ đếm "19/20" đổi sang màu cảnh báo (từ 18/20) | `01-near-limit.png` |
| 2 | Chọn 3 file theo thứ tự `a.pdf`, `b.pdf`, `c.pdf` | Nhận `a.pdf`; từ chối `b.pdf`, `c.pdf` kèm thông báo giới hạn | `02-accepted-1-rejected-2.png` |
| 3 | Quan sát bộ đếm và vùng kéo-thả | Bộ đếm "20/20"; vùng kéo-thả bị khóa | `03-full-20.png` |
| 4 | Cố tải thêm `d.pdf` | Hiển thị "Workspace đã đạt giới hạn 20 tài liệu. Xóa bớt tài liệu để tải lên tài liệu mới." | `04-limit-message.png` |
| 5 | Kiểm tra Network | Không có `POST /api/documents` cho các file bị từ chối | `05-no-request.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Số file nhận = 20 − số tài liệu hiện có, nhận đúng **thứ tự chọn**.
- [ ] Thông báo đúng nguyên văn AC-010.4.
- [ ] File bị từ chối không phát sinh request.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-UP-05/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-UP-05/screenshots", "test_report/TC-UP-05/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-UP-05"; $env:REPORT_DIR = "test_report/TC-UP-05"
  npx playwright test e2e/tests/upload/TC-UP-05.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-UP-05/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-near-limit.png`, `02-accepted-1-rejected-2.png`, `03-full-20.png`, `04-limit-message.png`, `05-no-request.png` |
| `video/TC-UP-05.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-UP-05.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-UP-05` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- Mock `near_limit` có 19 tài liệu (chọn 3 → nhận 1). Tình huống nguyên văn AC-010.5 (18 tài liệu, chọn 4 → nhận 2) chạy ở chế độ backend thật khi đã nạp sẵn 18 tài liệu.
