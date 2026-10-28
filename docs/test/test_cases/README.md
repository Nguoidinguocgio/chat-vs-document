# Test case — Chat với tài liệu (MVP lõi)

> Tách từ [Test Plan TP-CVD-001](../Test_Plan.md). **Mỗi file là một test case**; mỗi test case có sẵn nhiệm vụ tạo báo cáo kèm **ảnh chụp màn hình và video**, lưu tại `./test_report/{ID test case}/`.

**Tổng số:** 72 test case.

## 1. Cách dùng

1. Chọn một test case trong [danh mục](#3-danh-mục-test-case); mở file `TC-xxx.md` tương ứng.
2. Dựng môi trường đúng "Chế độ chạy" của test case (mục 2).
3. Thực hiện các bước ở mục 4 của file; chụp ảnh đúng tên ở cột "Ảnh" và quay video suốt lần chạy.
4. Làm theo mục 6 của file để tạo thư mục kết quả, điền `report.md`, `result.json`.
5. Cập nhật cột "Trạng thái" ở bảng dưới (Chưa chạy / Đạt / Không đạt / Bị chặn).

**Cấu trúc mỗi file:** mục tiêu → điều kiện tiên quyết → dữ liệu → bảng bước (hành động / kết quả mong đợi / ảnh) → tiêu chí đạt → **nhiệm vụ tạo báo cáo** → ghi chú.

## 2. Môi trường, URL và tài khoản

**URL kiểm thử: tất cả là `localhost`.**

| Thành phần | URL |
|---|---|
| Giao diện web (Vite) | http://localhost:5173 |
| Backend API | http://localhost:8080 (`/healthz` kiểm tra sống) |
| Giao diện, kịch bản API giả | http://localhost:5173/?mock=`<kịch bản>` |
| PostgreSQL | `localhost:5432` (DB `chatdoc`) |
| Ollama (embedding `bge-m3`) | `localhost:11434` |

**Tài khoản: dùng tài khoản mock** (định nghĩa trong `web/src/mocks/data.ts`, mật khẩu chung `Matkhau-2026`):

| Email | Mật khẩu | Hành vi |
|---|---|---|
| `lan@congty.vn` | `Matkhau-2026` | Member — vào thẳng workspace |
| `admin@congty.vn` | `Matkhau-2026` | Admin |
| `moi@congty.vn` | `Matkhau-2026` | Mật khẩu tạm → bắt buộc đổi mật khẩu |
| `khoa@congty.vn` | `bất kỳ` | Bị khóa tạm (423) |
| `vohieu@congty.vn` | `Matkhau-2026` | Tài khoản bị vô hiệu hóa |
| `minh@congty.vn` | `Matkhau-2026` | Member thứ hai — **chỉ có ở backend thật**, dùng cho kiểm thử hai phiên / phân quyền chéo |

Trên backend thật, tạo cùng các tài khoản bằng `./ops/scripts/create-user.ps1` với cùng mật khẩu để dữ liệu test thống nhất giữa các chế độ.

Kịch bản API giả `?mock=`: `answered` (mặc định), `not_found`, `partial`, `interrupted`, `timeout`, `llm_error`, `empty_workspace`, `processing_only`, `all_failed`, `near_limit`.

**Ba chế độ chạy:**

### `mock` — API giả (MSW) — frontend độc lập

- `cd web; npm run dev:mock` → http://localhost:5173

### `real` — Backend thật + `FakeLLM` / `FakeEmbedding`

- `./ops/scripts/up.ps1` (PostgreSQL 16 + pgvector, localhost:5432)
- Trong `.env`: `EMBEDDING_PROVIDER=fake`, `LLM_PROVIDER=fake`, `STREAM_STALL_SECONDS=3`, `LLM_FIRST_TOKEN_TIMEOUT_S=2`
- `./ops/scripts/api.ps1` → http://localhost:8080/healthz trả `{"status":"ok"}`
- `./ops/scripts/web.ps1` → http://localhost:5173
- Tạo tài khoản mock trên backend thật (cùng email và mật khẩu như API giả): `./ops/scripts/create-user.ps1 -Email lan@congty.vn -Name Lan -Role member -Password Matkhau-2026`; tương tự `admin@congty.vn` (`-Role admin`), `moi@congty.vn` (`-Temp`), `minh@congty.vn` (`-Role member`, ngữ cảnh thứ hai)

### `ai` — Backend thật + embedding `bge-m3` (Ollama) + LLM thật (`claude-sonnet-5-5`)

- Ollama chạy tại `localhost:11434` và đã `ollama pull bge-m3`
- Trong `.env`: `EMBEDDING_PROVIDER=ollama`, `LLM_PROVIDER=anthropic`, `LLM_API_KEY=<khóa dành cho kiểm thử>` (không commit)
- `./ops/scripts/up.ps1` → `./ops/scripts/api.ps1` → `./ops/scripts/web.ps1`
- Tạo tài khoản `lan@congty.vn` / `Matkhau-2026` bằng `create-user.ps1` như chế độ backend thật
- Corpus và bộ câu hỏi DEP-002 (`eval/corpus/`, `eval/dep002.yaml`) đã sẵn sàng và được Đội PTKD ký (G-03)

## 3. Danh mục test case

| ID | Tên | Ưu tiên | Loại | Chế độ | Truy vết | Trạng thái |
|---|---|---|---|---|---|---|
| **Smoke test** | | | | | | |
| [TC-SMOKE-01](TC-SMOKE-01.md) | Smoke test: đăng nhập → tải 1 PDF → hỏi 1 câu | P0 | Giao diện | Backend thật + Fake | Test Plan §4.2 (SC-1), §4.3 | Chưa chạy |
| **Xác thực và phiên** | | | | | | |
| [TC-AUTH-01](TC-AUTH-01.md) | Đăng nhập đúng email và mật khẩu | P0 | Giao diện | API giả | AC-001.1 · E-01 | Chưa chạy |
| [TC-AUTH-02](TC-AUTH-02.md) | Đăng nhập sai mật khẩu hoặc sai email | P0 | Giao diện | API giả | AC-001.2 · NFR-S03 · E-01 | Chưa chạy |
| [TC-AUTH-03](TC-AUTH-03.md) | Tài khoản bị khóa tạm sau nhiều lần sai | P0 | Giao diện | API giả | AC-001.3 · NFR-S03 · E-01 | Chưa chạy |
| [TC-AUTH-04](TC-AUTH-04.md) | Mật khẩu tạm bắt buộc đổi trước khi vào chat | P0 | Giao diện | API giả | AC-001.4 · E-01 | Chưa chạy |
| [TC-AUTH-05](TC-AUTH-05.md) | Hết phiên khi đang soạn: giữ nguyên nội dung nháp | P0 | Giao diện | API giả | AC-001.5 · PRD §5.2 · E-02 | Chưa chạy |
| [TC-AUTH-06](TC-AUTH-06.md) | Chưa đăng nhập: không truy cập được trang chat và API | P0 | Giao diện | Backend thật + Fake | AC-001.6 · NFR-S04 · E-03 | Chưa chạy |
| [TC-AUTH-07](TC-AUTH-07.md) | Vô hiệu hóa người dùng khi đang dùng: về đăng nhập ≤ 60 s | P0 | Giao diện | Backend thật + Fake | AC-003.1 · E-21 | Chưa chạy |
| **BB-01 Tải lên PDF** | | | | | | |
| [TC-UP-01](TC-UP-01.md) | Tải 1 PDF vào workspace trống → Đang xử lý → Sẵn sàng | P0 | Giao diện | API giả | AC-010.1 · AC-020.4 · AC-011.3 | Chưa chạy |
| [TC-UP-02](TC-UP-02.md) | Tải 5 file cùng lúc, trong đó 1 file hỏng | P0 | Giao diện | Backend thật + Fake | AC-010.3 · AC-012.6 | Chưa chạy |
| [TC-UP-03](TC-UP-03.md) | Từ chối file `.docx` đổi đuôi thành `.pdf` | P0 | Giao diện | API giả | AC-012.1 · ALG-02 · DD-04 | Chưa chạy |
| [TC-UP-04](TC-UP-04.md) | File 11 MB bị chặn ở client, không gửi yêu cầu mạng | P0 | Giao diện | API giả | AC-012.2 · ALG-02 | Chưa chạy |
| [TC-UP-05](TC-UP-05.md) | Giới hạn 20 tài liệu: nhận theo thứ tự chọn, từ chối phần dư | P0 | Giao diện | API giả | AC-010.4 · AC-010.5 · AC-011.3 | Chưa chạy |
| [TC-UP-06](TC-UP-06.md) | PDF quá 100 trang bị từ chối khi xử lý | P0 | Giao diện | API giả | AC-012.3 · AC-009 (MAX_PAGES) | Chưa chạy |
| [TC-UP-07](TC-UP-07.md) | PDF có mật khẩu bị từ chối | P0 | Giao diện | API giả | AC-012.4 | Chưa chạy |
| [TC-UP-08](TC-UP-08.md) | PDF dạng scan (không có lớp văn bản) bị từ chối | P0 | Giao diện | API giả | AC-012.5 · D-008 | Chưa chạy |
| [TC-UP-09](TC-UP-09.md) | PDF scan một phần: Sẵn sàng kèm cảnh báo trang không đọc được | P0 | Giao diện | API giả | AC-012.8 | Chưa chạy |
| [TC-UP-10](TC-UP-10.md) | Hai phiên cùng workspace: trạng thái đồng bộ ≤ 5 s | P0 | Giao diện | Backend thật + Fake | AC-011.2 · ALG-03 | Chưa chạy |
| [TC-UP-11](TC-UP-11.md) | Mất mạng giữa chừng khi tải: Gián đoạn → thử lại → nút "Thử lại" | P0 | Giao diện | API giả | PRD §5.2 · ST-01 · DD-09 | Chưa chạy |
| [TC-UP-12](TC-UP-12.md) | Cạnh tranh chỗ trống: hai người cùng tải khi còn 1 chỗ | P0 | Giao diện | Backend thật + Fake | PRD §5.2 · AC-010.4 · DOCUMENT_LIMIT_REACHED | Chưa chạy |
| [TC-UP-13](TC-UP-13.md) | Lỗi hệ thống khi xử lý → nút "Thử lại" | P0 | Giao diện | API giả | AC-012.7 · PRD §5.2 | Chưa chạy |
| **BB-02 Hỏi đáp có trích nguồn** | | | | | | |
| [TC-CHAT-01](TC-CHAT-01.md) | Hỏi đáp: câu trả lời hiển thị dần kèm trích dẫn | P0 | Giao diện | API giả | AC-020.1 · AC-020.3 · AC-022.1 · TH-01/02 | Chưa chạy |
| [TC-CHAT-02](TC-CHAT-02.md) | Hỏi đáp đa tài liệu: trích dẫn tới cả hai tài liệu | P0 | Giao diện | API giả | AC-021.1 · AC-021.2 · AC-022.1 | Chưa chạy |
| [TC-CHAT-03](TC-CHAT-03.md) | Hai tài liệu mâu thuẫn: nêu cả hai, không tự chọn bên | P0 | Giao diện | API giả | AC-021.3 | Chưa chạy |
| [TC-CHAT-04](TC-CHAT-04.md) | Ngoài phạm vi tài liệu: "Không tìm thấy", không có nguồn | P0 | Giao diện | API giả | AC-023.1 · AC-023.2 · TH-06 | Chưa chạy |
| [TC-CHAT-05](TC-CHAT-05.md) | Trả lời một phần: nêu phần tìm thấy và phần không tìm thấy | P0 | Giao diện | API giả | AC-023.3 | Chưa chạy |
| [TC-CHAT-06](TC-CHAT-06.md) | Hỏi bằng tiếng Việt trên tài liệu tiếng Anh | P0 | Giao diện | LLM thật | AC-024.1 · AC-024.2 · D-005 | Chưa chạy |
| [TC-CHAT-07](TC-CHAT-07.md) | Injection từ nội dung PDF và câu trả lời: render an toàn | P0 | Giao diện | Backend thật + Fake | NFR-S05 · R-4 · DD-06 | Chưa chạy |
| [TC-CHAT-08](TC-CHAT-08.md) | Ô nhập: giới hạn 2.000 ký tự, rỗng, Enter / Shift+Enter, bộ gõ tiếng Việt | P0 | Giao diện | API giả | ST-04 · AC-020.3 · PRD §5.2 | Chưa chạy |
| [TC-CHAT-09](TC-CHAT-09.md) | Bấm Dừng khi đang trả lời: dừng ≤ 1 s, có "Đã dừng" và Tạo lại | P1 | Giao diện | API giả | US-026 · AC-020.3 | Chưa chạy |
| [TC-CHAT-10](TC-CHAT-10.md) | Kết nối gián đoạn giữa chừng: giữ phần đã nhận, nhãn chưa hoàn chỉnh | P0 | Giao diện | API giả | PRD §5.2 · ST-02 · ALG-03 | Chưa chạy |
| [TC-CHAT-11](TC-CHAT-11.md) | LLM im lặng > 30 s: quá hạn, có nút "Thử lại" | P0 | Giao diện | API giả | PRD §5.2 · ST-02 · AC-020.2 | Chưa chạy |
| [TC-CHAT-12](TC-CHAT-12.md) | Dịch vụ AI lỗi / vượt hạn mức: thông báo thân thiện | P0 | Giao diện | API giả | PRD §5.2 · EV-024 | Chưa chạy |
| [TC-CHAT-13](TC-CHAT-13.md) | Bấm trích dẫn → Xem nguồn mở ≤ 1,5 s, đoạn gốc được làm nổi bật | P1 | Giao diện | API giả | AC-030.1 · AC-030.2 · DD-02 | Chưa chạy |
| [TC-CHAT-14](TC-CHAT-14.md) | Trích dẫn tới tài liệu đã bị xóa | P1 | Giao diện | Backend thật + Fake | AC-030.3 · PRD §5.2 | Chưa chạy |
| [TC-CHAT-15](TC-CHAT-15.md) | Ô nhập bị khóa khi chưa có tài liệu Sẵn sàng | P0 | Giao diện | API giả | AC-020.4 · ST-04 | Chưa chạy |
| [TC-CHAT-16](TC-CHAT-16.md) | Mất mạng khi đang soạn: giữ nội dung, khóa nút Gửi | P0 | Giao diện | API giả | PRD §5.2 | Chưa chạy |
| **Bảo mật và phân quyền** | | | | | | |
| [TC-SEC-01](TC-SEC-01.md) | Phân quyền chéo hội thoại: người B không đọc / ghi hội thoại của người A | P0 | Công cụ | Backend thật + Fake | G-07 · NFR-S04 · D-001 | Chưa chạy |
| [TC-SEC-02](TC-SEC-02.md) | Member không gọi được API Admin / Báo cáo | P0 | Công cụ | Backend thật + Fake | G-07 · NFR-S04 · AC-002.3 | Chưa chạy |
| [TC-SEC-03](TC-SEC-03.md) | Phiên hết hạn / bị thu hồi: cookie cũ không dùng lại được | P0 | Công cụ | Backend thật + Fake | G-07 · AC-001.5 · AC-003.1 | Chưa chạy |
| [TC-SEC-04](TC-SEC-04.md) | Dò mật khẩu: khóa 15 phút và phản hồi không phân biệt email | P0 | Công cụ | Backend thật + Fake | NFR-S03 · AC-001.3 | Chưa chạy |
| [TC-SEC-05](TC-SEC-05.md) | XSS hiển thị: ≥ 10 payload trong câu trả lời và trong văn bản PDF | P0 | Giao diện | Backend thật + Fake | NFR-S05 · R-4 · DD-06 | Chưa chạy |
| [TC-SEC-06](TC-SEC-06.md) | Prompt injection từ PDF trên LLM thật | P0 | Công cụ | LLM thật | NFR-S05 · RSK-001 | Chưa chạy |
| [TC-SEC-07](TC-SEC-07.md) | Tải lên độc hại: tên file nguy hiểm, PDF bom, 10 MB + 1 | P0 | Công cụ | Backend thật + Fake | NFR-S01 · DD-04 | Chưa chạy |
| [TC-SEC-08](TC-SEC-08.md) | Tải file PDF: không đăng nhập, ID lạ, Range bất thường | P0 | Công cụ | Backend thật + Fake | G-07 · AC-030.x | Chưa chạy |
| [TC-SEC-09](TC-SEC-09.md) | Open redirect ở `/login?next=` | P0 | Giao diện | Backend thật + Fake | NFR-S01 | Chưa chạy |
| [TC-SEC-10](TC-SEC-10.md) | Rò rỉ dữ liệu nghiệp vụ vào log, analytics, giám sát lỗi | P0 | Công cụ | Backend thật + Fake | Quy tắc bảo mật xuyên suốt · G-10 | Chưa chạy |
| [TC-SEC-11](TC-SEC-11.md) | CSRF: yêu cầu thay đổi dữ liệu từ trang khác nguồn bị chặn | P0 | Giao diện | Backend thật + Fake | NFR-S01 · SameSite=Strict | Chưa chạy |
| [TC-SEC-12](TC-SEC-12.md) | Giới hạn tải hỏi đáp: 4 câu đồng thời / 25 câu toàn hệ thống | P1 | Công cụ | Backend thật + Fake | T14 · CHAT_MAX_CONCURRENT | Chưa chạy |
| [TC-SEC-13](TC-SEC-13.md) | Xóa dữ liệu: file, chunk, vector biến mất ≤ 24 giờ | P1 | Công cụ | Backend thật + Fake | AC-060.3 | Chưa chạy |
| [TC-SEC-14](TC-SEC-14.md) | Quét toàn bộ route: 401 khi chưa đăng nhập, 403 khi thiếu header | P0 | Công cụ | Backend thật + Fake | G-06 · G-07 · NFR-S04 | Chưa chạy |
| [TC-SEC-15](TC-SEC-15.md) | OWASP ZAP baseline + active scan: 0 High / Critical | P0 | Công cụ | Backend thật + Fake | G-06 · NFR-S01 | Chưa chạy |
| [TC-SEC-16](TC-SEC-16.md) | Header bảo mật, cờ cookie, quét phụ thuộc và bí mật | P0 | Công cụ | Backend thật + Fake | G-06 · NFR-S01 · NFR-S02 | Chưa chạy |
| **Hiệu năng và thử tải** | | | | | | |
| [TC-PERF-01](TC-PERF-01.md) | L-1: Hỏi đáp đồng thời — TTFT p90 ≤ 3 s, hoàn chỉnh p90 ≤ 15 s | P0 | Công cụ | LLM thật | TH-01 · TH-02 · NFR-C01 · G-05 | Chưa chạy |
| [TC-PERF-02](TC-PERF-02.md) | L-2: Xử lý tài liệu 100 trang — p90 ≤ 60 s | P0 | Công cụ | LLM thật | TH-03 · AC-010.1 · G-05 | Chưa chạy |
| [TC-PERF-03](TC-PERF-03.md) | L-3: Kênh SSE workspace — 10 người giữ 30 phút | P1 | Công cụ | Backend thật + Fake | AC-011.2 · ALG-03 · G-05 | Chưa chạy |
| [TC-PERF-04](TC-PERF-04.md) | L-4: Tải trang ban đầu — LCP p75 ≤ 2 s, JS khởi tạo ≤ 200 KB gzip | P1 | Công cụ | Backend thật + Fake | TH-08 · NFR-P04 | Chưa chạy |
| [TC-PERF-05](TC-PERF-05.md) | L-5: Độ bền 2 giờ dưới tải trung bình | P1 | Công cụ | LLM thật | NFR-A01 · G-05 | Chưa chạy |
| **Khả năng tiếp cận** | | | | | | |
| [TC-A11Y-01](TC-A11Y-01.md) | axe-core: 0 lỗi serious / critical ở mọi trạng thái chính | P0 | Giao diện | API giả | NFR-U03 · WCAG 2.1 AA | Chưa chạy |
| [TC-A11Y-02](TC-A11Y-02.md) | Chỉ dùng bàn phím: đăng nhập → tải → hỏi → xem nguồn → đóng | P0 | Giao diện | API giả | NFR-U03 | Chưa chạy |
| [TC-A11Y-03](TC-A11Y-03.md) | Tương phản màu và thu phóng 200% | P0 | Giao diện | API giả | NFR-U03 · WCAG 2.1 AA | Chưa chạy |
| [TC-A11Y-04](TC-A11Y-04.md) | `prefers-reduced-motion`: không có hoạt ảnh | P1 | Giao diện | API giả | NFR-U03 | Chưa chạy |
| [TC-A11Y-05](TC-A11Y-05.md) | NVDA + Chrome: thông báo đúng khi hỏi đáp và đổi trạng thái | P0 | Thủ công | API giả | NFR-U03 · SDD 3.5.2 | Chưa chạy |
| **Chất lượng AI (DEP-002)** | | | | | | |
| [TC-AI-01](TC-AI-01.md) | TH-04: Trích dẫn trỏ đúng trang ≥ 90% | P0 | Công cụ | LLM thật | TH-04 · AC-022.3 · G-04 | Chưa chạy |
| [TC-AI-02](TC-AI-02.md) | TH-05: Câu trả lời đa tài liệu "đầy đủ" ≥ 80% | P0 | Công cụ | LLM thật | TH-05 · AC-021.1 · G-04 | Chưa chạy |
| [TC-AI-03](TC-AI-03.md) | TH-06: Câu hỏi ngoài phạm vi nhận "không tìm thấy" ≥ 90% | P0 | Công cụ | LLM thật | TH-06 · AC-023.1 · AC-023.2 · G-04 | Chưa chạy |
| [TC-AI-04](TC-AI-04.md) | TH-07: Câu trả lời bịa đặt ≤ 5% | P0 | Công cụ | LLM thật | TH-07 · AC-022.3 · RSK-001 · G-04 | Chưa chạy |
| [TC-AI-05](TC-AI-05.md) | Nhóm injection: không vi phạm, không lộ prompt hệ thống | P0 | Công cụ | LLM thật | NFR-S05 · G-04 | Chưa chạy |
| [TC-AI-06](TC-AI-06.md) | Hỏi chéo ngôn ngữ: chênh lệch ≤ 10 điểm % so với cùng ngôn ngữ | P0 | Công cụ | LLM thật | AC-024.1 · AC-024.2 · G-04 | Chưa chạy |
| [TC-AI-07](TC-AI-07.md) | Mọi câu hỏi kết thúc hợp lệ và mọi câu trả lời có trích dẫn | P0 | Công cụ | LLM thật | AC-020.2 · AC-022.2 · G-04 | Chưa chạy |
| **Chấp nhận người dùng** | | | | | | |
| [TC-UAT-01](TC-UAT-01.md) | UAT: ≥ 4/5 người hoàn thành "tải lên – hỏi – kiểm chứng" không cần hỗ trợ | P0 | Thủ công | LLM thật | G-12 · PER-01 | Chưa chạy |
| **Analytics** | | | | | | |
| [TC-ANA-01](TC-ANA-01.md) | Sự kiện analytics EV-010…EV-030 ghi đúng và không chứa nội dung | P0 | Công cụ | Backend thật + Fake | G-10 · PRD §6.2 · EV-010…EV-030 | Chưa chạy |

### Ma trận truy vết kịch bản trong Test Plan → test case

| Kịch bản trong Test Plan | Test case |
|---|---|
| E-01 (đăng nhập) | TC-AUTH-01, 02, 03, 04 |
| E-02 (hết phiên, giữ nháp) | TC-AUTH-05 |
| E-03 (chưa đăng nhập) | TC-AUTH-06 |
| E-04 · BB-01 T1 | TC-UP-01, TC-CHAT-15 |
| E-05 · BB-01 T2 | TC-UP-02 |
| E-06 · BB-01 T3, T4, T6 | TC-UP-03, 04, 06, 07, 08 (+ TC-UP-13 cho AC-012.7) |
| E-07 · BB-01 T7 | TC-UP-09 |
| E-08 · BB-01 T5 | TC-UP-05 |
| E-09 · BB-01 T8 | TC-UP-10 |
| E-10 · BB-01 T9 | TC-UP-11 |
| E-11 | TC-UP-12 |
| E-12 · BB-02 T1, T2, T3 | TC-CHAT-01, 02, 03 |
| E-13 | TC-CHAT-13 |
| E-14 · BB-02 T4, T5 | TC-CHAT-04, 05 |
| E-15 · BB-02 T6 | TC-CHAT-06 |
| E-16 · BB-02 T7 | TC-CHAT-07 |
| E-17 · BB-02 T8 | TC-CHAT-08 (+ TC-CHAT-16 cho mất mạng khi soạn) |
| E-18 · BB-02 T9 | TC-CHAT-09 |
| E-19 · BB-02 T10 | TC-CHAT-10, 11, 12 |
| E-20 · BB-02 T11 | TC-CHAT-14 |
| E-21 | TC-AUTH-07 |
| E-22 | TC-SEC-01 |
| BB-02 T12 · L-1…L-5 | TC-PERF-01 … TC-PERF-05 |
| S-01 … S-13 | TC-SEC-01 … TC-SEC-13 |
| Quét route · ZAP · header + quét phụ thuộc (T22 §3.2) | TC-SEC-14, 15, 16 |
| Khả năng tiếp cận (T21 §3.3) | TC-A11Y-01 … TC-A11Y-05 |
| DEP-002: TH-04…TH-07, AC-024, AC-020.2/022.2 | TC-AI-01 … TC-AI-07 |
| G-10 analytics | TC-ANA-01 |
| G-12 UAT | TC-UAT-01 |
| Smoke test (SC-1) | TC-SMOKE-01 |

## 4. Dữ liệu mẫu (fixtures)

Sinh bằng `api/src/test/java/com/chatvsdoc/api/support/MakeFixtures.java` và xuất vào `e2e/fixtures/` (T21). Chế độ API giả chỉ cần **file nhỏ bắt đầu bằng `%PDF-`** với tên chứa từ khóa.

| File | Dùng cho | Ghi chú |
|---|---|---|
| `ok_vi.pdf`, `ok_vi_30p.pdf` (~5 MB / 30 trang) | TC-UP-01, TC-SMOKE-01 | Tiếng Việt, có chữ "Điều khoản thanh toán" |
| `ok_en.pdf` | TC-CHAT-06 | Tiếng Anh "Payment terms … thirty days …" |
| `corrupted.pdf` | TC-UP-02, TC-ANA-01 | File hỏng, không mở được |
| `fake_docx.pdf` | TC-UP-03 | Nội dung Word, đuôi `.pdf` |
| `big_11mb.pdf` | TC-UP-04 | 11 MB, bắt đầu `%PDF-` |
| `too_many_101p.pdf` (mock: tên chứa `big`) | TC-UP-06 | 101 trang |
| `encrypted.pdf` (mock: tên chứa `pass`) | TC-UP-07 | Có mật khẩu |
| `scanned.pdf` (mock: tên chứa `scan`) | TC-UP-08 | Toàn trang scan |
| `mixed_scan.pdf` (mock: tên chứa `partial`) | TC-UP-09 | 3 trang scan + 10 trang văn bản |
| `injection.pdf` | TC-CHAT-07, TC-SEC-05/06 | Chứa chỉ thị độc hại |
| `eval/corpus/*`, `eval/dep002.yaml` | TC-AI-*, TC-SEC-06, TC-PERF-* | Đội PTKD soạn và ký (G-03) |

## 5. Ghi hình và chụp ảnh

Mọi artifact lưu theo `./test_report/{ID}/` — xem [test_report/README.md](../../../test_report/README.md).

**Tự động với Playwright** (khung ở T21; trích đoạn cấu hình cần có):

```ts
// e2e/playwright.config.ts (trích)
const tc = process.env.TC_ID ?? 'adhoc'
export default defineConfig({
  outputDir: `../test_report/${tc}/playwright-output`,
  use: { baseURL: 'http://localhost:5173', video: 'on', screenshot: 'off', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium' }, { name: 'msedge', use: { channel: 'msedge' } }],
})

// e2e/helpers/report.ts
export async function shot(page: Page, step: number, slug: string) {
  const dir = `../test_report/${process.env.TC_ID}/screenshots`
  await page.screenshot({ path: `${dir}/${String(step).padStart(2, '0')}-${slug}.png` })
}
// afterEach: đóng page rồi lưu video về đúng chỗ
test.afterEach(async ({ page }) => {
  const v = page.video(); await page.close()
  await v?.saveAs(`../test_report/${process.env.TC_ID}/video/${process.env.TC_ID}.webm`)
})
```

**Thủ công** (khi chưa có spec hoặc với công cụ dòng lệnh): quay màn hình bằng **Win+Alt+R** (Xbox Game Bar) hoặc OBS; chụp ảnh bằng **Win+Shift+S**. Đặt tên file đúng cột "Ảnh" của test case.

## 6. Báo cáo `result.json`

```json
{
  "test_case": "TC-UP-01",
  "status": "passed",
  "run_at": "2026-10-28T09:30:00+07:00",
  "runner": "tên người chạy hoặc ci",
  "commit": "ef9af50",
  "mode": "mock",
  "browser": "chromium 130",
  "steps": [{ "n": 1, "status": "passed", "screenshot": "screenshots/01-empty-workspace.png", "note": "" }],
  "video": "video/TC-UP-01.webm",
  "defects": []
}
```

`status` ∈ `passed` · `failed` · `blocked` · `not_run`. Mỗi `defects[]` là mã lỗi trong bug tracker.

## 7. Quy tắc chung

- **Thông điệp**: so khớp nguyên văn với PRD §4.3 / §5.2 và SDD 3.5.3; không tự đặt câu mới.
- **Bí mật**: không để lộ mật khẩu thật, `LLM_API_KEY`, cookie `sid` trong ảnh / video / log đính kèm. Tài khoản mock thì được phép.
- **Chạy lại**: khi test case phải chạy lại, giữ bằng chứng lần trước trong `attempt-<n>/`; thư mục gốc luôn là lần chạy mới nhất.
- **Bị chặn**: nếu thiếu điều kiện (ví dụ US-013 chưa có), kết luận **Bị chặn** kèm lý do — không đánh Đạt.
- **Lỗi**: mọi test case Không đạt phải có mã lỗi trong bug tracker và ghi vào `report.md`.
- **Kết quả AI** (`TC-AI-*`): chạy lặp ≥ 3 lần và ghi cố định phiên bản prompt / `TOP_K` / `MIN_SCORE` / mô hình.
