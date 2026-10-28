# TC-AUTH-05 — Hết phiên khi đang soạn: giữ nguyên nội dung nháp

| | |
|---|---|
| **Mã test case** | `TC-AUTH-05` |
| **Nhóm** | Xác thực và phiên |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-001.5 · PRD §5.2 · E-02 |
| **Mã trong test plan** | E-02 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=answered |
| **Thư mục kết quả** | `./test_report/TC-AUTH-05/` |
| **Spec tự động (dự kiến)** | `e2e/tests/auth/TC-AUTH-05.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Khi phiên hết hạn lúc người dùng đang soạn câu hỏi, hệ thống chuyển về đăng nhập và sau khi đăng nhập lại còn nguyên nội dung nháp (nháp chỉ ở `sessionStorage`).

## 2. Điều kiện tiên quyết

- `cd web; npm run dev:mock` → http://localhost:5173
- Giả lập hết phiên bằng Playwright: `page.route("**/api/**", r => r.fulfill({status: 401, json: {error: {code: "SESSION_EXPIRED", message_key: "auth.session_expired", details: {}}}}))`. Trên backend thật: xóa phiên trong CSDL.

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

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-AUTH-05/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` rồi vào trang chat | Trang chat hiển thị, ô nhập mở khóa | `01-chat.png` |
| 2 | Gõ nháp "Điều khoản thanh toán là gì?" vào ô nhập, **chưa gửi** | Ô nhập chứa đúng nội dung nháp | `02-draft-typed.png` |
| 3 | Giả lập hết phiên: chặn mọi `/api/**` trả `401 SESSION_EXPIRED`, rồi thực hiện một thao tác (ví dụ bấm Gửi) | Được chuyển về `/login` | `03-session-expired.png` |
| 4 | Bỏ chặn, đăng nhập lại `lan@congty.vn` | Vào lại trang chat và ô nhập vẫn chứa "Điều khoản thanh toán là gì?" | `04-draft-kept.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Chuyển về `/login` khi phiên hết hạn.
- [ ] Nội dung nháp còn nguyên sau khi đăng nhập lại.
- [ ] Nháp lưu ở `sessionStorage`, **không** có token trong `localStorage`.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-AUTH-05/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-AUTH-05/screenshots", "test_report/TC-AUTH-05/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-AUTH-05"; $env:REPORT_DIR = "test_report/TC-AUTH-05"
  npx playwright test e2e/tests/auth/TC-AUTH-05.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-AUTH-05/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 4 ảnh, một ảnh cho mỗi bước ở mục 4: `01-chat.png`, `02-draft-typed.png`, `03-session-expired.png`, `04-draft-kept.png` |
| `video/TC-AUTH-05.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-AUTH-05.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 4 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-AUTH-05` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
