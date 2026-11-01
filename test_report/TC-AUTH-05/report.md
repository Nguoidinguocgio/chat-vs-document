# TC-AUTH-05 — Hết phiên khi đang soạn: giữ nguyên nội dung nháp

Test case: [TC-AUTH-05](../../docs/test/test_cases/TC-AUTH-05.md) · Kết luận hiện tại: **Đạt** (Round 2). `result.json` ở thư mục này là bản sao kết quả của round mới nhất.

| Round | Ngày giờ (UTC+7) | Kết luận | Nội dung | Báo cáo |
|---|---|---|---|---|
| 1 | 2026-11-01 22:13 | Không đạt | Phát hiện **BUG-001**: nháp bị mất khi phiên hết hạn đúng lúc bấm Gửi | [round-1/report.md](round-1/report.md) |
| 2 | 2026-11-01 22:35 | **Đạt** | Xác nhận đã sửa BUG-001 (`holdDraft`/`releaseDraft`); thêm 4 test hồi quy Vitest | [round-2/report.md](round-2/report.md) |

| Mã lỗi | Mức độ | Trạng thái |
|---|---|---|
| BUG-001 | Major | Đã sửa — đã xác nhận ở Round 2 |
