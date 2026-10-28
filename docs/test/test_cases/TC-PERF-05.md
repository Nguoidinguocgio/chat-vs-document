# TC-PERF-05 — L-5: Độ bền 2 giờ dưới tải trung bình

| | |
|---|---|
| **Mã test case** | `TC-PERF-05` |
| **Nhóm** | Hiệu năng và thử tải |
| **Ưu tiên** | P1 |
| **Truy vết** | NFR-A01 · G-05 |
| **Mã trong test plan** | L-5 |
| **Loại** | Công cụ / dòng lệnh (k6, ZAP, curl, eval…) |
| **Chế độ chạy** | Backend thật + embedding `bge-m3` (Ollama) + LLM thật (`claude-sonnet-5-5`) |
| **URL kiểm thử** | http://localhost:5173 |
| **Thư mục kết quả** | `./test_report/TC-PERF-05/` |
| **Spec tự động (dự kiến)** | `e2e/tests/load/TC-PERF-05.spec.ts` |

> Thuộc [Test Plan TP-CVD-001](../Test_Plan.md). Quy ước chung, tài khoản và mẫu báo cáo: [README](README.md).

## 1. Mục tiêu

Chạy tải trung bình 2 giờ; bộ nhớ không tăng liên tục và độ trễ không tăng theo thời gian.

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

Cột **Ảnh** là tên file ảnh chụp bắt buộc tại bước đó (lưu vào `test_report/TC-PERF-05/screenshots/`).

| # | Hành động | Kết quả mong đợi | Ảnh |
|---|---|---|---|
| 1 | Khởi động giám sát tài nguyên | Ghi CPU / RAM / kết nối | `01-monitor.png` |
| 2 | Chạy k6 ở tải trung bình 2 giờ | Hoàn tất | `02-k6-2h.png` |
| 3 | Vẽ biểu đồ RAM và độ trễ p90 theo thời gian | RAM đi ngang; p90 không tăng đơn điệu | `03-charts.png` |
| 4 | Đếm lỗi cuối đợt | Tỷ lệ lỗi ≤ 2% | `04-errors.png` |

## 5. Tiêu chí đạt / không đạt

Test case **Đạt** khi *tất cả* điều kiện sau đúng; sai một điều kiện là **Không đạt**:

- [ ] Không rò rỉ bộ nhớ / kết nối.
- [ ] Độ trễ không tăng theo thời gian.
- [ ] Thông điệp hiển thị khớp **nguyên văn** PRD/SDD (so khớp chuỗi, không chỉ "chứa từ khóa").
- [ ] Đủ bộ báo cáo ở mục 6 (ảnh từng bước, video, `report.md`, `result.json`). Thiếu bằng chứng = chưa hoàn thành test case.

## 6. Nhiệm vụ tạo báo cáo kiểm thử (bắt buộc)

Người chạy test case này **phải** tạo báo cáo có ảnh chụp màn hình và video, lưu tại `./test_report/TC-PERF-05/`.

**Bước 6.1 — Tạo thư mục kết quả**

```powershell
New-Item -ItemType Directory -Force "test_report/TC-PERF-05/screenshots", "test_report/TC-PERF-05/video" | Out-Null
```

**Bước 6.2 — Chạy test và ghi hình**

- Chạy các lệnh ở mục 4 trong một cửa sổ terminal; **quay video toàn bộ phiên chạy** (Win+Alt+R hoặc OBS), gồm cả lúc khởi động và kết quả cuối.
- Chụp ảnh terminal / dashboard / báo cáo công cụ ở mỗi bước, đặt tên đúng cột "Ảnh".
- Lưu đầu ra thô của công cụ vào `test_report/TC-PERF-05/` (xem mục 6.3). Không để lộ khóa API / cookie / mật khẩu thật trong ảnh và video — che hoặc dùng khóa kiểm thử.

**Bước 6.3 — Bộ artifacts phải có trong `test_report/TC-PERF-05/`**

| Artifact | Bắt buộc | Nội dung |
|---|---|---|
| `screenshots/NN-<slug>.png` | Có | 4 ảnh, một ảnh cho mỗi bước ở mục 4: `01-monitor.png`, `02-k6-2h.png`, `03-charts.png`, `04-errors.png` |
| `video/TC-PERF-05.webm` (hoặc `.mp4`) | Có | Video liên tục toàn bộ lần chạy; nếu chạy lại, giữ video của lần chạy cuối cùng và đặt lần trước vào `video/attempt-1/` |
| `report.md` | Có | Báo cáo theo [mẫu](../../../test_report/_template/report.md): kết quả từng bước, Đạt/Không đạt, môi trường, thời gian, người chạy, liên kết ảnh + video, mã lỗi (nếu có) |
| `result.json` | Có | Kết quả máy đọc được (schema ở [README §6](README.md#6-báo-cáo-resultjson)) |
| `resources.csv` | Có | Tài nguyên theo thời gian |
| `k6.json` | Có | Đầu ra thô |

**Bước 6.4 — Điền `report.md`**

1. Sao chép `test_report/_template/report.md` vào thư mục kết quả rồi điền: mã test case, ngày giờ, người chạy, commit (`git rev-parse --short HEAD`), trình duyệt + phiên bản, chế độ chạy.
2. Với **mỗi bước**: ghi kết quả thực tế, trạng thái Đạt/Không đạt, chèn ảnh `![bước N](screenshots/NN-<slug>.png)`.
3. Chèn liên kết video `[video](video/TC-PERF-05.webm)` và (nếu có) mã lỗi trong bug tracker.
4. Ghi **kết luận**: Đạt / Không đạt / Bị chặn, kèm lý do ngắn.

**Bước 6.5 — Kiểm tra trước khi nộp**

- [ ] Đủ 4 ảnh đúng tên; mở từng ảnh thấy rõ nội dung cần kiểm (không đen, không che mất thông điệp).
- [ ] Video phát được, bao trùm toàn bộ các bước, không chứa mật khẩu / khóa API thật.
- [ ] `report.md` và `result.json` thống nhất kết luận.
- [ ] Cập nhật trạng thái `TC-PERF-05` trong [bảng tổng hợp](README.md#3-danh-mục-test-case).
