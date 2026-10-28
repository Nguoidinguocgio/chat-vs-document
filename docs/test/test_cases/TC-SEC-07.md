# TC-SEC-07 — Tải lên độc hại: tên file nguy hiểm, PDF bom, 10 MB + 1

| | |
|---|---|
| **Mã test case** | `TC-SEC-07` |
| **Nhóm** | Bảo mật và phân quyền |
| **Ưu tiên** | P0 |
| **Truy vết** | NFR-S01 · DD-04 |
| **Mã trong test plan** | S-07 |
| **Loại** | Công cụ / dòng lệnh (k6, ZAP, curl, eval…) |
| **Chế độ chạy** | Backend thật + `FakeLLM` / `FakeEmbedding` |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-SEC-07/` |
| **Spec tự động (dự kiến)** | `e2e/tests/security/TC-SEC-07.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Hệ thống từ chối / xử lý an toàn các file độc hại, không ghi ngoài thư mục lưu, không sập dịch vụ.

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

- PDF bom sinh bằng script (đặt trong `e2e/security/`)

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-SEC-07/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Tải file tên `../../x.pdf`, `<script>.pdf`, tên có ký tự điều khiển Unicode (bằng API trực tiếp) | Không ghi ra ngoài `STORAGE_DIR`; tên hiển thị được escape | `01-dangerous-names.png` |
| 2 | Tải file `.docx` đổi đuôi `.pdf` | Bị từ chối đúng thông báo | `02-fake-ext.png` |
| 3 | Tải PDF "bom" (hàng nghìn trang nhỏ, nén cao) | Bị từ chối hoặc xử lý có giới hạn thời gian / bộ nhớ; dịch vụ vẫn sống (`/healthz`) | `03-pdf-bomb.png` |
| 4 | Tải file đúng 10 MB + 1 byte trực tiếp tới API (bỏ qua client) | Bị máy chủ từ chối | `04-oversize-server.png` |
| 5 | Kiểm tra thư mục `storage/` và log | Không có file lạ ngoài thư mục lưu; log không có lỗi chưa xử lý | `05-storage-clean.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Không có đường dẫn thoát khỏi thư mục lưu.
- [ ] Dịch vụ vẫn phản hồi `/healthz` sau mỗi file độc hại.
- [ ] Máy chủ tự kiểm tra ràng buộc (không tin client).
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-SEC-07/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-SEC-07/screenshots", "test_report/TC-SEC-07/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Chạy các lệnh ở mục 4 trong một cửa sổ terminal; **quay video toàn bộ phiên chạy** (Win+Alt+R hoặc OBS), gồm cả lúc khởi động và kết quả cuối.
- Chụp ảnh terminal / dashboard / báo cáo công cụ ở mỗi bước, đặt tên đúng cột "Ảnh".
- Lưu đầu ra thô của công cụ vào `test_report/TC-SEC-07/` (xem mục 6.3). Không để lộ khóa API / cookie / mật khẩu thật trong ảnh và video — che hoặc dùng khóa kiểm thử.

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-SEC-07/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-dangerous-names.png`, `02-fake-ext.png`, `03-pdf-bomb.png`, `04-oversize-server.png`, `05-storage-clean.png` |
| `video/TC-SEC-07.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-SEC-07.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-SEC-07` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
