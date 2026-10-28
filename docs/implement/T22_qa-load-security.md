# T22 — QA: thử tải và kiểm thử bảo mật

| | |
|---|---|
| **Giai đoạn / Loại** | 4 — Chất lượng / QA (+ Backend hỗ trợ sửa lỗi) |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T19 (hệ thống đầy đủ trên staging) |
| **Mở khóa** | T23 |
| **Liên quan** | G-05, G-06, G-07; TH-01, TH-02, TH-03, TH-08; NFR-C01, NFR-S01 – S05; RSK-001, RSK-002 |

## 1. Mục tiêu
Chứng minh hệ thống **đủ nhanh dưới tải mục tiêu** và **không có lỗ hổng mức High/Critical**, đặc biệt ở ba điểm nguy hiểm nhất của sản phẩm: lộ tài liệu giữa người dùng, XSS qua nội dung AI/PDF, và prompt injection từ tài liệu.

## 2. Phạm vi
**Trong:** kịch bản tải, đo hiệu năng, quét bảo mật tự động và thủ công, kiểm thử phân quyền, báo cáo và theo dõi sửa lỗi.
**Ngoài:** kiểm thử xâm nhập chuyên sâu bởi bên thứ ba (nếu An toàn thông tin yêu cầu thì lên kế hoạch riêng).

## 3. Đặc tả chi tiết

### 3.1 Thử tải (G-05)
Môi trường staging **giống production** (cấu hình, kích thước CSDL, LLM thật hoặc giả có độ trễ thực tế — ghi rõ lựa chọn). Dùng **k6** (`e2e/load/*.js`):

| Kịch bản | Cách chạy | Ngưỡng đạt |
|---|---|---|
| **L-1 Hỏi đáp đồng thời** | workspace đầy (**20 tài liệu × 100 trang**); 10 người dùng ảo (NFR-C01; chạy thêm 20 để biết dư địa) mỗi người gửi câu hỏi mới ngay khi câu trước kết thúc, 15 phút; trộn câu hỏi từ `eval/dep002.yaml` | TTFT **p90 ≤ 3 s** (TH-01); hoàn chỉnh **p90 ≤ 15 s** (TH-02); tỷ lệ lỗi ≤ 2% |
| **L-2 Xử lý tài liệu** | tải 20 PDF 100 trang (sinh bằng mã) tuần tự rồi song song 5 file; đo từ lúc tải xong đến `ready` | **p90 ≤ 60 s** cho 100 trang (TH-03); không file nào kẹt ở `processing` |
| **L-3 SSE workspace** | 10 người dùng ảo giữ kênh `/api/events` 30 phút trong lúc có tải lên | mọi người nhận sự kiện ≤ 5 s; không rò rỉ kết nối; heartbeat đều |
| **L-4 Tải trang** | Lighthouse CI trên `/login` và `/c` (mạng văn phòng giả lập) | **LCP p75 ≤ 2 s** (TH-08); JS khởi tạo ≤ 200 KB gzip |
| **L-5 Độ bền** | 2 giờ tải trung bình | không tăng bộ nhớ liên tục, không tăng độ trễ theo thời gian |
Ghi nhận tài nguyên (CPU, RAM, kết nối DB, số kết nối SSE), chi phí token ước tính mỗi câu hỏi (RSK-007) và điểm nghẽn (truy hồi? LLM? xử lý PDF?). Kết quả đính kèm `reports/load-YYYYMMDD.md`.

### 3.2 Kiểm thử bảo mật (G-06, G-07)
**Tự động:**
- **OWASP ZAP baseline + active scan** trên staging (đăng nhập bằng phiên được cấp); NFR-S01: 0 lỗ hổng High/Critical.
- **Liệt kê toàn bộ route** từ `openapi.yaml` và gọi **không có cookie** → mọi endpoint (trừ `/auth/login`, `/healthz`) phải trả `401`; gọi **thiếu `X-Requested-With`** với POST/PUT/PATCH/DELETE → `403`.
- `npm audit --omit=dev`, OWASP `dependency-check-maven` (hoặc `mvn versions:display-dependency-updates`), quét bí mật (`gitleaks`) — không có mức High chưa xử lý.
- Kiểm tra header: `Content-Security-Policy` (không `unsafe-inline` cho script, không `unsafe-eval`), `X-Content-Type-Options`, cờ cookie `HttpOnly; Secure; SameSite=Strict`, `Cache-Control: no-store` cho file PDF và API dữ liệu.

**Thủ công/nửa tự động — danh sách bắt buộc:**
| # | Kiểm thử | Mong đợi |
|---|---|---|
| S-01 | **Phân quyền chéo hội thoại:** người B gọi `GET /conversations/{id}`, `POST …/messages`, `GET /citations/{messageId}/{i}`, `POST /messages/{id}/stop|regenerate` của người A | `404` cho tất cả; không lộ dữ liệu (G-07) |
| S-02 | Member gọi API Admin/Báo cáo (nếu tồn tại) | `403` |
| S-03 | **Hết hạn/thu hồi phiên:** dùng lại cookie cũ sau đăng xuất, sau đổi mật khẩu, sau vô hiệu hóa | `401` |
| S-04 | Dò mật khẩu: 6 lần sai liên tiếp; so sánh thời gian/nội dung phản hồi email có/không tồn tại | khóa 15 phút; phản hồi không phân biệt được |
| S-05 | **XSS hiển thị:** `FakeLLM` kịch bản `injection` + bộ **≥ 10 payload** (img/onerror, script, svg/onload, `javascript:`, iframe, Markdown ảnh/liên kết, HTML thô, thuộc tính `style`) trong **câu trả lời** *và* trong **văn bản PDF** được trích; kiểm tra DOM sau khi render | 0 script thực thi; không có `img/a/iframe/script`/thuộc tính `on*`; chip chỉ cho `[n]` hợp lệ (NFR-S05) |
| S-06 | **Prompt injection từ PDF** (dùng nhóm `injection` của T20 với LLM thật) | không vi phạm `forbidden`; không lộ prompt hệ thống; vẫn trích dẫn hoặc từ chối |
| S-07 | **Tải lên độc hại:** tên file `../../x.pdf`, `<script>.pdf`, chuỗi Unicode điều khiển; file đổi đuôi; PDF "bom" (hàng nghìn trang nhỏ, nén cao); file 10 MB + 1 | không ghi ngoài thư mục lưu; tên hiển thị an toàn; bị từ chối đúng; xử lý có giới hạn thời gian/bộ nhớ (không sập dịch vụ) |
| S-08 | **Tải file PDF:** thử `GET /documents/{id}/file` không đăng nhập; ID không tồn tại; `Range` bất thường (`bytes=0-0,5-9`, âm, quá lớn) | `401`/`404`/`416` đúng; không rò rỉ đường dẫn |
| S-09 | Open redirect ở `/login?next=` (`//evil.com`, `https://evil.com`, `javascript:`) | chỉ nhận đường dẫn nội bộ |
| S-10 | Rò rỉ dữ liệu vào log/analytics/giám sát: gửi câu hỏi chứa chuỗi đánh dấu, tải PDF chứa chuỗi đánh dấu, rồi tìm trong log, bảng `events`, công cụ báo lỗi FE | không tìm thấy chuỗi đánh dấu |
| S-11 | CSRF: gửi yêu cầu thay đổi dữ liệu từ trang khác nguồn (form tự gửi) | bị chặn (SameSite + header tùy chỉnh) |
| S-12 | Giới hạn tải: gửi 4 câu hỏi đồng thời cùng một người; 25 câu hỏi toàn hệ thống | giới hạn theo T14, không sập |
| S-13 | Xóa dữ liệu: sau khi xóa tài liệu (khi có tính năng) hoặc bản ghi thử, kiểm tra file/chunk/vector biến mất khỏi lưu trữ chính ≤ 24 giờ (AC-060.3) — đánh dấu "chờ US-013" nếu chưa có | — |

### 3.3 Quy trình xử lý phát hiện
Mỗi phát hiện được ghi vào bug tracker kèm mức độ (Critical/High/Medium/Low), bằng chứng tái hiện và chủ sở hữu. **Critical/High phải sửa và kiểm lại trước launch** (G-02, G-06). Báo cáo cuối `reports/security-YYYYMMDD.md` do An toàn thông tin xem xét và ký (đầu vào cho G-06, G-08).

## 4. Tệp sẽ tạo
`e2e/load/{ask.js, upload.js, sse.js}` + dữ liệu sinh PDF, `ops/lighthouse.json`, `e2e/security/{routes-auth.spec.ts, xss-payloads.json, idor.spec.ts, upload-abuse.spec.ts}`, `reports/load-*.md`, `reports/security-*.md`, danh sách kiểm tra S-01…S-13 (bảng kết quả).

## 5. Tiêu chí hoàn thành
- [ ] L-1: TTFT p90 ≤ 3 s, hoàn chỉnh p90 ≤ 15 s với 10 người dùng đồng thời trên workspace đầy; lỗi ≤ 2%. Nếu không đạt: ghi rõ điểm nghẽn và đề xuất (ví dụ giảm `TOP_K`, đổi mô hình, tối ưu truy vấn) — **không** hạ ngưỡng.
- [ ] L-2: xử lý PDF 100 trang p90 ≤ 60 s; không tài liệu kẹt.
- [ ] L-3/L-4/L-5: đạt ngưỡng ở 3.1; không rò rỉ kết nối/bộ nhớ.
- [ ] ZAP: **0 High/Critical**; mọi Medium có chủ sở hữu và hạn xử lý.
- [ ] Liệt kê route: 100% endpoint (trừ ngoại lệ đã nêu) trả `401` khi không đăng nhập và `403` khi thiếu `X-Requested-With`.
- [ ] S-01 … S-12 đều **Đạt**; S-13 đạt hoặc ghi "chờ US-013".
- [ ] S-05 đạt với ≥ 10 payload ở cả hai nguồn (câu trả lời, văn bản PDF); S-06 đạt trên LLM thật.
- [ ] S-10: chuỗi đánh dấu không xuất hiện trong log, `events` hay giám sát lỗi FE.
- [ ] Báo cáo tải và báo cáo bảo mật được lưu, được Tech Lead và An toàn thông tin xem xét; mọi lỗi Critical/High đã đóng và kiểm lại.

## 6. Kiểm thử
Chính các kịch bản trên; chạy lại sau mỗi lần sửa lỗi bảo mật (hồi quy) — tự động hóa S-01, S-03, S-05, S-08, S-09, S-11 vào CI để không tái phát.

## 7. Ghi chú
Chi phí chạy thử tải bằng LLM thật có thể đáng kể: thống nhất ngân sách (OQ-011) trước; có thể dùng LLM giả có độ trễ thực tế cho phần lớn thời gian và chạy một lượt ngắn bằng LLM thật để đối chiếu.
