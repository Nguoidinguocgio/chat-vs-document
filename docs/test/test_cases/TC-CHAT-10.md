# TC-CHAT-10 — Kết nối gián đoạn giữa chừng: giữ phần đã nhận, nhãn chưa hoàn chỉnh

| | |
|---|---|
| **Mã test case** | `TC-CHAT-10` |
| **Nhóm** | BB-02 Hỏi đáp có trích nguồn |
| **Ưu tiên** | P0 |
| **Truy vết** | PRD §5.2 · ST-02 · ALG-03 |
| **Mã trong test plan** | BB-02 T10 · E-19 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | API giả (MSW) — frontend độc lập |
| **URL kiểm thử** | http://localhost:5173/?mock=interrupted |
| **Thư mục kết quả** | `./test_report/TC-CHAT-10/` |
| **Spec tự động (dự kiến)** | `e2e/tests/chat/TC-CHAT-10.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Mất kết nối khi đang nhận câu trả lời thì giữ phần đã có, gắn nhãn chưa hoàn chỉnh, có nút Tạo lại; câu dở dang không bao giờ trông như hoàn chỉnh.

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

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-CHAT-10/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` rồi gõ "Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?" rồi bấm Gửi | Câu trả lời bắt đầu hiện | `01-streaming.png` |
| 2 | Kịch bản ngắt kết nối giữa chừng (`?mock=interrupted`) | Dừng nhận dữ liệu; hiện biểu ngữ "Mất kết nối" ở đầu trang | `02-connection-lost.png` |
| 3 | Đọc thẻ câu trả lời | Có nhãn "Kết nối bị gián đoạn — câu trả lời chưa hoàn chỉnh" và viền cảnh báo | `03-incomplete-label.png` |
| 4 | Quan sát nút | Có nút "Tạo lại"; **không** tự gửi lại câu hỏi | `04-regenerate-button.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Nhãn đúng nguyên văn PRD §5.2.
- [ ] Phần đã nhận được giữ lại.
- [ ] Không tự gửi lại (tránh tốn chi phí LLM).
- [ ] Có chỉ báo ngoài màu (icon + chữ).
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-CHAT-10/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-CHAT-10/screenshots", "test_report/TC-CHAT-10/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-CHAT-10"; $env:REPORT_DIR = "test_report/TC-CHAT-10"
  npx playwright test e2e/tests/chat/TC-CHAT-10.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-CHAT-10/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 4 ảnh, một ảnh cho mỗi bước ở mục 4: `01-streaming.png`, `02-connection-lost.png`, `03-incomplete-label.png`, `04-regenerate-button.png` |
| `video/TC-CHAT-10.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-CHAT-10.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 4 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-CHAT-10` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
