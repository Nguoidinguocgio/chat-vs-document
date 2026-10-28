# T23 — Sẵn sàng phát hành: giám sát, UAT, runbook, hướng dẫn

| | |
|---|---|
| **Giai đoạn / Loại** | 4 — Phát hành / Vận hành + Sản phẩm |
| **Ước tính** | 3 ngày công kỹ thuật (UAT và ký duyệt do con người, cần lên lịch sớm) |
| **Phụ thuộc** | T20, T21, T22 (và hệ thống chạy trên staging) |
| **Mở khóa** | Quyết định launch |
| **Liên quan** | G-08, G-10, G-11, G-12, G-13, G-14; NFR-O01, NFR-A01; PRD §6.1 (tiêu chí dừng/rollback), §6.2 (analytics) |

## 1. Mục tiêu
Biến một hệ thống "chạy được" thành hệ thống **vận hành được và ra mắt được một cách có kiểm soát**: có giám sát và cảnh báo, có sao lưu và đường lui, người dùng thật được thử trước, và mọi chữ ký bắt buộc đã đủ.

## 2. Phạm vi
**Trong:** giám sát và cảnh báo, kiểm tra analytics trên staging, sao lưu/khôi phục, kế hoạch rollback đã diễn tập, UAT, hướng dẫn sử dụng 1 trang, bảng kiểm launch gate.
**Ngoài:** các tính năng chưa nằm trong hai story (xem mục 9 của [kế hoạch](implement_plan.md)) — cần kế hoạch bổ sung trước launch thật.

## 3. Đặc tả chi tiết

### 3.1 Giám sát và cảnh báo (G-13)
| Hạng mục | Yêu cầu |
|---|---|
| Lỗi frontend | Công cụ báo lỗi (tự lưu trữ hoặc dịch vụ được duyệt) với **bộ lọc loại bỏ nội dung**: chỉ gửi loại lỗi, route, `app_version`, mã lỗi; **cấm** gửi câu hỏi, câu trả lời, tên tài liệu, văn bản DOM, giá trị ô nhập. Kiểm tra bằng test (S-10 ở T22). |
| Backend | Log có cấu trúc (đã có ở T03) gom tập trung; số liệu: độ trễ yêu cầu, TTFT (`ttft_ms`), tổng thời gian trả lời, tỷ lệ `error`, hàng đợi xử lý tài liệu, số kết nối SSE, lỗi LLM theo mã. |
| Cảnh báo | **Tỷ lệ lỗi câu trả lời > 10% trong 1 giờ** (tiêu chí rollback của PRD §6.1); TTFT p90 > 3 s kéo dài 15 phút; hàng đợi xử lý kẹt > 10 phút; lỗi 5xx > 2%; đĩa/DB gần đầy; tài liệu kẹt `processing` > 5 phút. |
| Chi phí LLM (NFR-O01) | Tổng token × đơn giá **theo ngày** từ `messages.llm_*_tokens`; cảnh báo khi vượt ngưỡng ngày (ngưỡng do Quản lý chốt — OQ-011); bảng theo dõi chi phí mỗi câu hỏi. |
| Sức khỏe | `GET /healthz` (sống) và `GET /readyz` (CSDL + chỗ lưu file + cấu hình LLM sẵn sàng); giám sát bên ngoài gọi mỗi phút. |

### 3.2 Kiểm tra analytics trên staging (G-10)
Bảng kiểm bằng cách thực hiện thật các hành động rồi truy vấn bảng `events`: EV-001, EV-002, EV-010, EV-011, EV-012, EV-013, EV-020, EV-021, EV-022, EV-023, EV-024, EV-025, EV-030, EV-050. Mỗi sự kiện: có ghi đúng một lần, thuộc tính chung đủ (`event_id`, `timestamp`, `user_id`, `session_id`, `role`, `app_version`), **không** chứa nội dung. Xuất bảng kết quả Đạt/Không đạt ký bởi Product Owner. (Báo cáo tuần của US-050 nằm ngoài lát cắt này; chỉ cần dữ liệu thô đúng.)

### 3.3 Sao lưu, khôi phục, rollback
- **Sao lưu hằng ngày** CSDL (gồm vector) và thư mục tài liệu; lưu ≥ 7 ngày [Đề xuất]; chính sách và thời gian giữ cuối cùng do An toàn thông tin chốt (OQ-022).
- **Diễn tập khôi phục:** khôi phục bản sao lưu vào môi trường trống, đăng nhập, mở tài liệu, hỏi một câu; ghi lại thời gian và các bước.
- **Rollback:** phiên bản phát hành được gắn thẻ; có lệnh/pipeline quay về phiên bản trước (ứng dụng) và quy tắc xử lý migration CSDL (chỉ migration tương thích ngược trong cửa sổ rollback; migration phá hủy tách riêng sau). **Diễn tập** rollback trên staging và ghi lại thời gian.
- **Tiêu chí dừng dịch vụ (PRD §6.1):** phát hiện rò rỉ tài liệu giữa người dùng, hoặc tỷ lệ lỗi câu trả lời > 10% trong 1 giờ, hoặc sự cố bảo mật được ATTT xác nhận → tạm ngừng và điều tra. Có công tắc "chế độ bảo trì" (trang thông báo, chặn ghi) để thực hiện nhanh.

### 3.4 Runbook (`ops/runbook.md`)
Ngắn gọn, theo tình huống: LLM lỗi/vượt hạn mức · hàng đợi xử lý tài liệu kẹt · SSE bị proxy đệm (bật chế độ thăm dò dự phòng — T11) · đĩa đầy · khôi phục từ sao lưu · rollback · vô hiệu hóa khẩn cấp một tài khoản · thu hồi toàn bộ phiên · đổi khóa API LLM · cách đọc nhật ký kiểm toán; mỗi mục có lệnh, người liên hệ, thời gian xử lý dự kiến.

### 3.5 UAT (G-12)
- **Người tham gia:** ≥ **5** người dùng thuộc PER-01 (Đội PTKD), chưa tham gia phát triển.
- **Kịch bản** (không hướng dẫn trước): đăng nhập bằng mật khẩu tạm và đổi mật khẩu → tải lên 2–3 PDF thật → chờ sẵn sàng → hỏi một câu đa tài liệu → bấm trích dẫn để kiểm chứng → hỏi một câu không có trong tài liệu → nhận xét.
- **Đo:** hoàn thành nhiệm vụ cốt lõi **"tải lên – hỏi – kiểm chứng trích dẫn" không cần hỗ trợ**: yêu cầu **≥ 4/5 người**; thời gian hoàn thành, số lỗi/nhầm lẫn, câu hỏi họ đặt, điểm hài lòng 1–5, lời nhận xét tự do (đặc biệt về độ tin cậy của câu trả lời).
- **Biên bản UAT** (`docs/uat/UAT-<ngày>.md`): kết quả từng người, lỗi phát hiện (phân loại), quyết định. Phát hiện mức Blocker/Critical phải sửa trước launch.

### 3.6 Hướng dẫn sử dụng 1 trang (G-14)
Nội dung bắt buộc (tiếng Việt, 1 trang, kèm ảnh chụp từ ứng dụng thật): (1) cách đăng nhập và đổi mật khẩu; (2) cách tải lên (giới hạn: **20 file, ≤ 10 MB, ≤ 100 trang, PDF có văn bản — chưa hỗ trợ PDF scan**) và ý nghĩa từng trạng thái; (3) cách hỏi (Việt/Anh, tối đa 2.000 ký tự); (4) **cách kiểm chứng: bấm `[n]` để xem đoạn gốc — "AI có thể sai, hãy kiểm tra trích dẫn trước khi dùng"**; (5) "Không tìm thấy" nghĩa là gì và cần làm gì; (6) liên hệ khi gặp lỗi. Gắn vào mục "Hướng dẫn sử dụng" trong menu tài khoản.

### 3.7 Bảng kiểm launch gate (`docs/launch/launch-checklist.md`)
Bảng G-01 … G-14 của PRD §6.1: trạng thái, bằng chứng (liên kết báo cáo), người ký. Điền các mục thuộc lát cắt hai story (G-01, G-02, G-03, G-04, G-05, G-06, G-07, G-09, G-10, G-11, G-12, G-13, G-14); G-08 (ATTT + Pháp chế ký) nhắc ở đầu bảng vì **thiếu chữ ký = không launch**. Mục chưa thuộc lát cắt (US-050 báo cáo, US-061 nhật ký đầy đủ, quản trị người dùng) ghi rõ "chưa làm" và là điều kiện bổ sung.

## 4. Tệp sẽ tạo
`ops/monitoring/*` (cấu hình cảnh báo, bảng điều khiển), `ops/runbook.md`, `ops/backup/*`, `docs/uat/UAT-plan.md` + biên bản, `docs/guides/huong-dan-su-dung.md` (+ bản PDF/ảnh), `docs/launch/launch-checklist.md`, `api/src/main/java/com/chatvsdoc/api/web/HealthController.java` (`/readyz`, kiểm tra kết nối DB qua Actuator `HealthIndicator`), bảng kiểm analytics.

## 5. Tiêu chí hoàn thành
- [ ] Giám sát lỗi FE và BE hoạt động; **kiểm chứng** không có nội dung nghiệp vụ trong công cụ giám sát (G-13, S-10).
- [ ] Mọi cảnh báo ở 3.1 đã cấu hình và được thử kích hoạt ít nhất một lần; cảnh báo chi phí LLM theo ngày hoạt động (NFR-O01).
- [ ] Bảng kiểm analytics ở 3.2 tất cả **Đạt**, Product Owner ký (G-10).
- [ ] Sao lưu chạy tự động; **diễn tập khôi phục** thành công và có ghi chép thời gian.
- [ ] **Diễn tập rollback** thành công trên staging và ghi chép; công tắc bảo trì hoạt động.
- [ ] Runbook đầy đủ các tình huống ở 3.4, được một người **không** viết ra nó thử làm theo thành công ở ít nhất hai tình huống.
- [ ] UAT: ≥ 5 người PER-01, **≥ 4/5 hoàn thành "tải lên – hỏi – kiểm chứng" không cần hỗ trợ**; biên bản được Product Owner ký (G-12); không còn lỗi Blocker/Critical.
- [ ] Hướng dẫn 1 trang hoàn tất, có nêu giới hạn và yêu cầu kiểm chứng trích dẫn, được gắn trong ứng dụng (G-14).
- [ ] `launch-checklist.md` điền đầy đủ cho các mục thuộc lát cắt, có liên kết bằng chứng và người ký; G-08 (ATTT + Pháp chế) đã ký; báo cáo T20 (G-03/G-04/G-11), T21 (G-01/G-09), T22 (G-05/G-06/G-07) đính kèm.
- [ ] Danh sách rõ ràng các mục **P0 còn lại ngoài lát cắt** (quản trị người dùng US-002/003, báo cáo US-050, nhật ký kiểm toán đầy đủ US-061) với kế hoạch và thời hạn — để launch thật không bỏ sót.

## 6. Kiểm thử
Diễn tập (khôi phục, rollback, chuyển bảo trì), kích hoạt thử cảnh báo, rà soát chéo runbook và hướng dẫn; UAT với người dùng thật.

## 7. Ghi chú
Lên lịch UAT, buổi ký duyệt với An toàn thông tin/Pháp chế và việc cấp phát tài khoản cho người thử **từ đầu Giai đoạn 4** (hoặc sớm hơn): đây là những việc phụ thuộc vào lịch của người khác và là nguyên nhân phổ biến nhất khiến phát hành bị trễ.
