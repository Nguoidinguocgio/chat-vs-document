# TC-CHAT-06 — Hỏi bằng tiếng Việt trên tài liệu tiếng Anh

| | |
|---|---|
| **Mã test case** | `TC-CHAT-06` |
| **Nhóm** | BB-02 Hỏi đáp có trích nguồn |
| **Ưu tiên** | P0 |
| **Truy vết** | AC-024.1 · AC-024.2 · D-005 |
| **Mã trong test plan** | BB-02 T6 · E-15 |
| **Loại** | Giao diện (tự động Playwright hoặc thủ công có ghi hình) |
| **Chế độ chạy** | Backend thật + embedding `bge-m3` (Ollama) + LLM thật (`claude-sonnet-5-5`) |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-CHAT-06/` |
| **Spec tự động (dự kiến)** | `e2e/tests/chat/TC-CHAT-06.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Hỏi tiếng Việt trên tài liệu tiếng Anh vẫn trả lời bằng tiếng Việt và trích dẫn đúng tài liệu tiếng Anh.

## 2. Điều kiện tiên quyết

- Ollama chạy tại `localhost:11434` và đã `ollama pull bge-m3`
- Trong `.env`: `EMBEDDING_PROVIDER=ollama`, `LLM_PROVIDER=anthropic`, `LLM_API_KEY=<khóa dành cho kiểm thử>` (không commit)
- `./ops/scripts/up.ps1` → `./ops/scripts/api.ps1` → `./ops/scripts/web.ps1`
- Tạo tài khoản `lan@congty.vn` / `Matkhau-2026` bằng `create-user.ps1` như chế độ backend thật
- Corpus và bộ câu hỏi DEP-002 (`eval/corpus/`, `eval/dep002.yaml`) đã sẵn sàng và được Đội PTKD ký (G-03)

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

## 4. Các bước thực hiện

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-CHAT-06/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Đăng nhập bằng `lan@congty.vn` / `Matkhau-2026` | Workspace có `ok_en.pdf` ("Payment terms … within thirty days …") Sẵn sàng | `01-ready.png` |
| 2 | Gõ "Thời hạn thanh toán là bao nhiêu ngày?" rồi gửi | Câu trả lời hiển thị dần | `02-ask.png` |
| 3 | Đọc nội dung | Trả lời **bằng tiếng Việt**, nêu 30 ngày | `03-vi-answer.png` |
| 4 | Đọc nguồn | Có trích dẫn tới `ok_en.pdf` đúng trang | `04-citation-en.png` |
| 5 | Gõ lại câu hỏi bằng tiếng Anh "What are the payment terms?" | Trả lời bằng tiếng Anh | `05-en-answer.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Ngôn ngữ trả lời trùng ngôn ngữ câu hỏi.
- [ ] Trích dẫn đúng tài liệu và trang.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-CHAT-06/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-CHAT-06/screenshots", "test_report/TC-CHAT-06/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Tự động: 
  ```powershell
  $env:TC_ID = "TC-CHAT-06"; $env:REPORT_DIR = "test_report/TC-CHAT-06"
  npx playwright test e2e/tests/chat/TC-CHAT-06.spec.ts --project=chromium --project=msedge
  ```
  Cấu hình Playwright ghi video (`video: "on"`) và gọi `shot(page, <số bước>, <slug>)` ở mỗi bước (xem [README §5](README.md#5-ghi-hình-và-chụp-ảnh)).
- Thủ công (khi chưa có spec): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS ngay từ trước bước 1 đến hết bước cuối, chụp ảnh bằng **Win+Shift+S**, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-CHAT-06/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-ready.png`, `02-ask.png`, `03-vi-answer.png`, `04-citation-en.png`, `05-en-answer.png` |
| `video/TC-CHAT-06.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `trace.zip` | Khi chạy Playwright | Playwright trace (`trace: "retain-on-failure"`); luôn lưu khi Không đạt |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-CHAT-06.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-CHAT-06` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- Cần LLM thật nên chạy ở chế độ `ai`. Tỷ lệ so sánh Việt–Anh (chênh ≤ 10 điểm %) đo bằng `TC-AI-06`.
