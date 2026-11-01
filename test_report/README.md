# test_report — Kết quả và bằng chứng kiểm thử

Mỗi test case lưu kết quả tại `./test_report/{ID test case}/`. Mô tả test case nằm ở [docs/test/test_cases/](../docs/test/test_cases/README.md).

```
test_report/
├── _template/report.md            # mẫu báo cáo — sao chép vào thư mục test case
└── TC-UP-01/
    ├── report.md                  # báo cáo: kết quả từng bước + ảnh + video + kết luận
    ├── result.json                # kết quả máy đọc được
    ├── screenshots/
    │   ├── 01-empty-workspace.png # một ảnh cho mỗi bước, tên theo cột "Ảnh" của test case
    │   └── …
    ├── video/
    │   └── TC-UP-01.webm          # video liên tục toàn bộ lần chạy
    ├── trace.zip                  # (Playwright) khi có
    └── …                          # đầu ra thô của công cụ (k6.json, zap-report.html, eval/report.md…)
```

**Vòng kiểm thử (round):** khi chạy lại một test case để xác nhận đã sửa lỗi, mỗi vòng có một thư mục con riêng:
- `round-1/`, `round-2/`, … mỗi thư mục chứa đủ bộ artifact như trên.
- `report.md` ở gốc thư mục test case là mục lục các vòng, kèm trạng thái lỗi.
- `result.json` ở gốc là bản sao kết quả của vòng mới nhất, có trường `report_dir` trỏ tới thư mục vòng đó.

Chạy một vòng mới:
```powershell
cd e2e
$env:TC_ID="TC-AUTH-05"; $env:REPORT_DIR="test_report/TC-AUTH-05/round-2"
npx playwright test tests/auth/TC-AUTH-05.spec.ts --project=chromium
```
`attempt-<n>/` vẫn dùng cho các lần chạy lại *trong cùng một vòng*, ví dụ khi phải sửa spec.

**Lưu ý:** thư mục `TC-*/` được tạo khi chạy test (xem mục 6 của từng test case). Video và ảnh có dung lượng lớn — nếu không muốn đưa vào Git, thêm `test_report/**/*.webm`, `test_report/**/*.mp4` vào `.gitignore` và lưu ở kho artifact của CI.
