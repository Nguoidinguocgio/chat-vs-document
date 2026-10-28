# TC-UP-01 — Tải 1 PDF vào workspace trống → Đang xử lý → Sẵn sàng

| | |
|---|---|
| **Mã test case** | `TC-UP-01` |
| **Nhóm** | BB-01 Tải lên PDF |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-010.1 · AC-020.4 · AC-011.3 |
| **Mã trong test plan** | BB-01 T1 · E-04 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=empty_workspace |
| **Thư mục kết quả** | `./test_report/TC-UP-01/` |
| **Spec tự động (dự kiến)** | `e2e/tests/upload/TC-UP-01.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Từ workspace trống, tải 1 PDF hợp lệ và thấy trạng thái chuyển Đang xử lý → Sẵn sàng, có toast, ô nhập mở khóa.

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

- `ok_vi_30p.pdf` — PDF hợp lệ ~5 MB / 30 trang

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-UP-01/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` | Workspace trống: lời dẫn, nút "Tải lên", giới hạn; ô nhập bị khóa kèm "Tải lên ít nhất một tài liệu PDF để bắt đầu hỏi" | `01-empty-workspace.png` |
| 2 | Kéo `ok_vi_30p.pdf` (5 MB, 30 trang) vào cửa sổ trình duyệt (chưa thả) | Lớp phủ kéo-thả toàn màn hình hiện lên | `02-drag-overlay.png` |
| 3 | Thả file | File xuất hiện trong danh sách với "Đang xử lý" và thanh tiến trình riêng | `03-processing.png` |
| 4 | Chờ xử lý xong | Trạng thái "Sẵn sàng"; toast "Đã sẵn sàng. Hãy đặt câu hỏi." | `04-ready-toast.png` |
| 5 | Quan sát ô nhập và đầu bảng tài liệu | Ô nhập mở khóa; 3 câu hỏi gợi ý; bộ đếm "1/20 tài liệu" | `05-composer-enabled.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Trạng thái đúng thứ tự Đang xử lý → Sẵn sàng.
- [ ] Toast đúng nguyên văn.
- [ ] Bộ đếm "1/20 tài liệu".
- [ ] Với backend thật: xử lý xong ≤ 60 s p90 (đo bằng `TC-PERF-02`).
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-UP-01/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-UP-01/screenshots", "test_report/TC-UP-01/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-UP-01"; $env:REPORT_DIR = "test_report/TC-UP-01"
  npx playwright test e2e/tests/upload/TC-UP-01.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-UP-01/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-empty-workspace.png`, `02-drag-overlay.png`, `03-processing.png`, `04-ready-toast.png`, `05-composer-enabled.png` |
| `video/TC-UP-01.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-UP-01.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-UP-01` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- File mẫu: sinh bằng `MakeFixtures` (T08) và xuất vào `e2e/fixtures/` (T21). Chế độ API giả chỉ cần file nhỏ bắt đầu bằng `%PDF-` và đặt tên chứa từ khóa.
