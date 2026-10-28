# Báo cáo kiểm thử — {TC-ID}: {tên test case}

| | |
|---|---|
| **Test case** | [{TC-ID}](../../docs/test/test_cases/{TC-ID}.md) |
| **Kết luận** | ☐ Đạt  ☐ Không đạt  ☐ Bị chặn |
| **Ngày giờ chạy** | {YYYY-MM-DD HH:mm} |
| **Người chạy** | {tên / CI} |
| **Commit** | {git rev-parse --short HEAD} |
| **Chế độ chạy** | {mock / real / ai} |
| **Trình duyệt / công cụ** | {Chromium 130 / Edge 130 / k6 / ZAP …} |
| **URL** | {http://localhost:5173 …} |
| **Tài khoản** | {lan@congty.vn (mock)} |
| **Video** | [{TC-ID}.webm](video/{TC-ID}.webm) |

## Kết quả từng bước

### Bước 1 — {hành động}
- **Mong đợi:** {kết quả mong đợi từ test case}
- **Thực tế:** {mô tả}
- **Trạng thái:** ☐ Đạt  ☐ Không đạt
- ![bước 1](screenshots/01-{slug}.png)

### Bước 2 — {hành động}
- **Mong đợi:** …
- **Thực tế:** …
- **Trạng thái:** ☐ Đạt  ☐ Không đạt
- ![bước 2](screenshots/02-{slug}.png)

*(lặp cho tất cả các bước)*

## Số liệu đo (nếu có)

| Chỉ số | Ngưỡng | Đo được | Đạt? |
|---|---|---|---|
| {ví dụ: thời gian đăng nhập} | ≤ 2 s | {…} | ☐ |

## Lỗi phát hiện

| Mã lỗi | Mức độ | Mô tả | Bước |
|---|---|---|---|
| {BUG-…} | {Blocker/Critical/Major/Minor} | {…} | {n} |

## Kết luận

{Đạt / Không đạt / Bị chặn — lý do ngắn gọn, lần chạy lại nếu có (attempt-n)}

## Bằng chứng đính kèm

- [ ] Đủ ảnh từng bước trong `screenshots/`
- [ ] Video trong `video/`
- [ ] `result.json`
- [ ] Đầu ra thô của công cụ (nếu có)
