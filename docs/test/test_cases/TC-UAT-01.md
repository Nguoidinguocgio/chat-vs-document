# TC-UAT-01 — UAT: ≥ 4/5 người hoàn thành "tải lên – hỏi – kiểm chứng" không cần hỗ trợ

| | |
|---|---|
| **Mã test case** | `TC-UAT-01` |
| **Nhóm** | Chấp nhận người dùng |
| **Ưu tiên** | P0 |
| **Truy vết** | G-12 · PER-01 |
| **Mã trong test plan** | G-12 |
| **Loại** | Thủ công có ghi hình |
| **Chế độ chạy** | Backend thật + embedding `bge-m3` (Ollama) + LLM thật (`claude-sonnet-5-5`) |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-UAT-01/` |
| **Spec tự động (dự kiến)** | `e2e/tests/uat/TC-UAT-01.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Ít nhất 5 người dùng thuộc PER-01 hoàn thành kịch bản; ít nhất 4/5 người hoàn thành nhiệm vụ không cần hỗ trợ.

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

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-UAT-01/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Giới thiệu 1 trang (G-14) cho người tham gia; đăng nhập bằng tài khoản mock `lan@congty.vn` / `Matkhau-2026` | Người tham gia vào được workspace | `01-briefing.png` |
| 2 | Người tham gia tải lên một PDF của dự án | Tài liệu Sẵn sàng không cần hỗ trợ | `02-upload.png` |
| 3 | Người tham gia đặt một câu hỏi thật của công việc | Nhận câu trả lời có trích dẫn | `03-ask.png` |
| 4 | Người tham gia bấm trích dẫn và xác nhận nguồn đúng/sai | Hoàn thành kiểm chứng | `04-verify-citation.png` |
| 5 | Quan sát viên ghi: thời gian, số lần cần hỗ trợ, điểm khó | Có biên bản cho từng người | `05-observation.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] ≥ 5 người tham gia.
- [ ] ≥ 4/5 người hoàn thành cả ba bước mà không cần hỗ trợ.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-UAT-01/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-UAT-01/screenshots", "test_report/TC-UAT-01/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Quay màn hình (Win+Alt+R hoặc OBS) từ trước bước 1 đến hết bước cuối, **kèm tiếng nói / phụ đề** nếu là kiểm thử có trình đọc màn hình.
- Chụp ảnh ở mỗi bước, đặt tên đúng cột "Ảnh".

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-UAT-01/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 5 ảnh, một ảnh cho mỗi bước ở mục 4: `01-briefing.png`, `02-upload.png`, `03-ask.png`, `04-verify-citation.png`, `05-observation.png` |
| `video/TC-UAT-01.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `participants/P1..P5/` | Có | Mỗi người một thư mục con có ảnh + video màn hình + biên bản quan sát |
| `uat-summary.md` | Có | Bảng tổng hợp 5 người, tỷ lệ hoàn thành, các điểm khó |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-UAT-01.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 5 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-UAT-01` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).

## 7. Ghi chú

- Xin phép người tham gia trước khi quay màn hình; che thông tin nhạy cảm của dự án.
