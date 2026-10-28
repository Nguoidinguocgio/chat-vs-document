# TC-SEC-05 — XSS hiển thị: ≥ 10 payload trong câu trả lời và trong văn bản PDF

| | |
|---|---|
| **Mã test case** | `TC-SEC-05` |
| **Nhóm** | Bảo mật và phân quyền |
| **Ưu tiên** | P0 |
| **Truy vết** | NFR-S05 · R-4 · DD-06 |
| **Mã trong test plan** | S-05 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | Backend thật + `FakeLLM` / `FakeEmbedding` |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-SEC-05/` |
| **Spec tự động (dự kiến)** | `e2e/tests/security/TC-SEC-05.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Không payload nào thực thi hoặc để lại phần tử nguy hiểm trong DOM, dù nằm trong câu trả lời của LLM hay trong văn bản PDF được trích.

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

- Payload tối thiểu: `<img src=x onerror=...>`, `<script>`, `<svg onload=...>`, `javascript:` trong liên kết Markdown, `<iframe>`, Markdown ảnh `![](x)`, Markdown liên kết, HTML thô, thuộc tính `style`, `<a href="javascript:...">`

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-SEC-05/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Nạp danh sách ≥ 10 payload (`e2e/security/xss-payloads.json`) | Có đủ các loại trong mục dữ liệu | `01-payload-list.png` |
| 2 | Cho `FakeLLM` kịch bản `injection` trả lần lượt từng payload trong **câu trả lời**; hỏi và mở DOM | Mỗi payload hiển thị dạng văn bản / bị loại bỏ | `02-answer-payloads.png` |
| 3 | Tải PDF có payload trong **văn bản**, hỏi để đoạn đó được trích vào câu trả lời | Như bước 2 | `03-pdf-payloads.png` |
| 4 | Tìm trong DOM: `script`, `img`, `a`, `iframe`, `svg`, thuộc tính `on*`, `style`, `javascript:` | Không có (ngoại trừ chip `[n]` hợp lệ) | `04-dom-audit.png` |
| 5 | Theo dõi `window.__xss` / `dialog` event | Không có hộp thoại, không có biến bị đặt | `05-no-execution.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] 0 script thực thi ở cả hai nguồn.
- [ ] Không còn `img/a/iframe/script`, thuộc tính `on*` trong vùng nội dung LLM.
- [ ] Chip chỉ thành nút với `[n]` có dữ liệu trích dẫn.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-SEC-05/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-SEC-05/screenshots", "test_report/TC-SEC-05/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-SEC-05"; $env:REPORT_DIR = "test_report/TC-SEC-05"
  npx playwright test e2e/tests/security/TC-SEC-05.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-SEC-05/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-payload-list.png`, `02-answer-payloads.png`, `03-pdf-payloads.png`, `04-dom-audit.png`, `05-no-execution.png` |
| `video/TC-SEC-05.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |
| `dom-snapshot.html` | Có | Bản chụp DOM vùng câu trả lời sau khi render, cho từng nhóm payload |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-SEC-05.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-SEC-05` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
