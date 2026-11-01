# TC-A11Y-01 — axe-core: 0 lỗi serious / critical ở mọi trạng thái chính

Test case: [TC-A11Y-01](../../docs/test/test_cases/TC-A11Y-01.md) · Kết luận hiện tại: **Bị chặn (một phần)** (Round 2). `result.json` ở thư mục này là bản sao kết quả của round mới nhất.

| Round | Ngày giờ (UTC+7) | Kết luận | axe (critical / serious / moderate) | Nội dung | Báo cáo |
|---|---|---|---|---|---|
| 1 | 2026-11-01 22:13 | Không đạt | 0 / 18 / 9 | Phát hiện BUG-002, BUG-003, BUG-004; bảng Xem nguồn bị chặn (T18) | [round-1/report.md](round-1/report.md) |
| 2 | 2026-11-01 22:35 | **Bị chặn (một phần)** | 0 / **0** / 9 | Xác nhận đã sửa BUG-002; bước 1–3 Đạt; bước 4 vẫn chờ T18 | [round-2/report.md](round-2/report.md) |

| Mã lỗi | Mức độ | Trạng thái |
|---|---|---|
| BUG-002 | Major | Đã sửa — đã xác nhận ở Round 2 |
| BUG-003 | Minor (moderate, `page-has-heading-one`) | Còn mở |
| BUG-004 | Minor (icon đè chữ ở trang 403) | Còn mở |

Còn lại: Round 3 khi T18 (bảng Xem nguồn) xong, kèm chạy thêm `msedge`.
