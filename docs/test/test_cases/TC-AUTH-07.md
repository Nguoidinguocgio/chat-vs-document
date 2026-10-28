# TC-AUTH-07 — Vô hiệu hóa người dùng khi đang dùng: về đăng nhập ≤ 60 s

| | |
|---|---|
| **Mã test case** | `TC-AUTH-07` |
| **Nhóm** | Xác thực và phiên |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-003.1 · E-21 |
| **Mã trong test plan** | E-21 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | Backend thật + `FakeLLM` / `FakeEmbedding` |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-AUTH-07/` |
| **Spec tự động (dự kiến)** | `e2e/tests/auth/TC-AUTH-07.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Khi tài khoản bị vô hiệu hóa lúc đang mở ứng dụng, yêu cầu tiếp theo bị từ chối và người dùng bị đưa về trang đăng nhập trong ≤ 60 giây.

## 2. Điều kiện tiên quyết

- `./ops/scripts/up.ps1` (PostgreSQL 16 + pgvector, localhost:5432)
- Trong `.env`: `EMBEDDING_PROVIDER=fake`, `LLM_PROVIDER=fake`, `STREAM_STALL_SECONDS=3`, `LLM_FIRST_TOKEN_TIMEOUT_S=2`
- `./ops/scripts/api.ps1` → http://localhost:8080/healthz trả `{"status":"ok"}`
- `./ops/scripts/web.ps1` → http://localhost:5173
- Tạo tài khoản mock trên backend thật (cùng email và mật khẩu như API giả): `./ops/scripts/create-user.ps1 -Email lan@congty.vn -Name Lan -Role member -Password Matkhau-2026`; tương tự `admin@congty.vn` (`-Role admin`), `moi@congty.vn` (`-Temp`), `minh@congty.vn` (`-Role member`, ngữ cảnh thứ hai)

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
| `minh@congty.vn` | `Matkhau-2026` | Member thứ hai (chỉ có khi tạo trên backend thật) |

**Dữ liệu riêng của test case:**

- `vohieu@congty.vn` ở API giả minh họa nhánh đăng nhập tài khoản bị vô hiệu hóa; kịch bản "đang dùng" cần backend thật.

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-AUTH-07/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập `minh@congty.vn` / `Matkhau-2026` và giữ trang chat mở | Trang chat hiển thị bình thường | `01-logged-in.png` |
| 2 | Ở terminal khác: `java -jar api/target/api.jar --spring.profiles.active=cli disable-user --email minh@congty.vn` | Lệnh báo vô hiệu hóa thành công | `02-cli-disable.png` |
| 3 | Quay lại trình duyệt, không thao tác gì; đo thời gian | Trong ≤ 60 giây tự được chuyển về `/login` (kênh sự kiện / poll nhận 401) | `03-redirected.png` |
| 4 | Đăng nhập lại `minh@congty.vn` | Bị từ chối; không tạo phiên mới | `04-login-denied.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Thời gian từ lệnh vô hiệu hóa tới khi bị đưa về `/login` ≤ 60 s.
- [ ] Không thể đăng nhập lại.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-AUTH-07/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-AUTH-07/screenshots", "test_report/TC-AUTH-07/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-AUTH-07"; $env:REPORT_DIR = "test_report/TC-AUTH-07"
  npx playwright test e2e/tests/auth/TC-AUTH-07.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-AUTH-07/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 4 ảnh, một ảnh cho mỗi bước ở mục 4: `01-logged-in.png`, `02-cli-disable.png`, `03-redirected.png`, `04-login-denied.png` |
| `video/TC-AUTH-07.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-AUTH-07.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 4 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-AUTH-07` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- Tài liệu của người bị vô hiệu hóa vẫn được dùng để trả lời (AC-003.3) — kiểm bằng cách hỏi một câu trên tài liệu do người đó tải.
