# TC-AI-02 — TH-05: Câu trả lời đa tài liệu "đầy đủ" ≥ 80%

| | |
|---|---|
| **Mã test case** | `TC-AI-02` |
| **Nhóm** | Chất lượng AI (DEP-002) |
| **Ưu tiên** | P0 |
| **Truy vết** | TH-05 · AC-021.1 · G-04 |
| **Mã trong test plan** | DEP-002 · TH-05 |
| **Loại** | Công cụ / dòng lệnh (k6, ZAP, curl, eval…) |
| **Chế độ chạy** | Backend thật + embedding `bge-m3` (Ollama) + LLM thật (`claude-sonnet-5-5`) |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-AI-02/` |
| **Spec tự động (dự kiến)** | `e2e/tests/eval/TC-AI-02.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Chuyên gia chấm tỷ lệ câu trả lời đầy đủ cho nhóm câu hỏi đa tài liệu đạt ≥ 80%.

## 2. Điều kiện tiên quyết

- Ollama chạy tại `localhost:11434` và đã `ollama pull bge-m3`
- Trong `.env`: `EMBEDDING_PROVIDER=ollama`, `LLM_PROVIDER=anthropic`, `LLM_API_KEY=<khóa dành cho kiểm thử>` (không commit)
- `./ops/scripts/up.ps1` → `./ops/scripts/api.ps1` → `./ops/scripts/web.ps1`
- Tạo tài khoản `lan@congty.vn` / `Matkhau-2026` bằng `create-user.ps1` như chế độ backend thật
- Corpus và bộ câu hỏi DEP-002 (`eval/corpus/`, `eval/dep002.yaml`) đã sẵn sàng và được Đội PTKD ký (G-03)
- Bộ DEP-002 đã được Đội PTKD ký (G-03); ghi cố định phiên bản prompt, `RETRIEVAL_TOP_K`, `RETRIEVAL_MIN_SCORE`, mô hình trong `report.md`.
- Chạy lặp **≥ 3 lần** và báo cáo trung bình + khoảng (LLM không xác định — rủi ro TR-03).

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

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-AI-02/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | `python eval/run.py --base-url http://localhost:8080 --user lan@congty.vn --password Matkhau-2026 --only multi` (≥ 10 câu) | Chạy xong; sinh `grading.csv` | `01-run-multi.png` |
| 2 | Hai chuyên gia chấm độc lập cột `complete (0/1)` cho mẫu 15 câu | Đồng thuận ≥ 80% | `02-inter-rater.png` |
| 3 | Chuyên gia chấm toàn bộ nhóm `multi` | Cột `complete` được điền đủ | `03-grading-done.png` |
| 4 | `python eval/score.py grading.csv` | TH-05 ≥ 80% | `04-th05-result.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] TH-05 ≥ 80%.
- [ ] Đồng thuận giữa hai chuyên gia ≥ 80% trên mẫu 15 câu.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-AI-02/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-AI-02/screenshots", "test_report/TC-AI-02/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Chạy các lệnh ở mục 4 trong một cửa sổ terminal; **quay video toàn bộ phiên chạy** (Win+Alt+R hoặc OBS), gồm cả lúc khởi động và kết quả cuối.
- Chụp ảnh terminal / dashboard / báo cáo công cụ ở mỗi bước, đặt tên đúng cột "Ảnh".
- Lưu đầu ra thô của công cụ vào `test_report/TC-AI-02/` (xem mục 6.3). Không để lộ khóa API / cookie / mật khẩu thật trong ảnh và video — che hoặc dùng khóa kiểm thử.

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-AI-02/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 4 ảnh, một ảnh cho mỗi bước ở mục 4: `01-run-multi.png`, `02-inter-rater.png`, `03-grading-done.png`, `04-th05-result.png` |
| `video/TC-AI-02.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `eval/report.md` | Có | Báo cáo của `eval/run.py` (Đạt/Không đạt theo ngưỡng, danh sách câu thất bại, so sánh hồi quy) |
| `eval/results.json` | Có | Kết quả thô từng câu |
| `grading.csv` | Có | Bảng chấm đã điền của chuyên gia |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-AI-02.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 4 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-AI-02` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
