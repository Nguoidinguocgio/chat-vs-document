# CLAUDE.md — Hướng dẫn làm việc trong dự án "Chat với tài liệu"

> Tài liệu cho người và AI agent làm việc trong repo này. Tổng hợp từ [BRD](requirements/BRD.md), [PRD](requirements/PRD.md), [Test Plan](test/Test_Plan.md), [Implement Plan](implement/implement_plan.md), [README gốc](../README.md), [api/README](../api/README.md) và CI.

## 1. Sản phẩm trong một đoạn

Ứng dụng web cho đội phát triển kinh doanh: đăng nhập → tải PDF lên **một workspace chung** → hỏi bằng tiếng Việt/Anh → nhận câu trả lời streaming **có trích dẫn tên tài liệu + số trang** kiểm chứng được; không có căn cứ thì **từ chối ("Không tìm thấy thông tin này trong các tài liệu hiện có.") chứ không bịa**. Niềm tin vào câu trả lời là trụ cột sản phẩm (PRD §0, RSK-001).

**Ranh giới MVP (đã chốt, D-001 – D-008):** 1 workspace; tối đa **20 file**, mỗi file **≤ 10 MB và ≤ 100 trang**; chỉ **PDF có lớp văn bản** (không OCR, PDF scan bị từ chối); tiếng Việt + Anh; tài khoản do Admin tạo (email + mật khẩu, không tự đăng ký); hội thoại riêng tư, lưu 90 ngày; Chrome/Edge, màn hình ≥ 1280 px.

**Phạm vi triển khai hiện tại:** chỉ lát cắt xương sống **BB-01 Tải lên PDF** + **BB-02 Hỏi đáp có trích nguồn** + đăng nhập tối thiểu (23 task T01–T23 trong [implement/README.md](implement/README.md)). Quản trị người dùng đầy đủ, xóa/tóm tắt/trùng tài liệu, lịch sử hội thoại, 👍/👎, báo cáo tuần, OCR là **ngoài phạm vi kế hoạch hiện tại**; đừng làm lan sang. Lưu ý: US-002/003, US-050, US-061 vẫn là P0 trước launch và cần kế hoạch bổ sung (Test Plan TR-13).

## 2. Lệnh build / chạy / test

Cần: JDK 21, Node ≥ 20 (CI dùng 20), Docker Desktop (PostgreSQL 16 + pgvector và Testcontainers). Maven dùng wrapper `api/mvnw`.

| Việc | Windows (PowerShell, tại gốc repo) | Linux/macOS/Git Bash |
|---|---|---|
| Bật PostgreSQL | `./ops/scripts/up.ps1` | `make up` |
| Chạy backend (`:8080`, `/healthz`) | `./ops/scripts/api.ps1` | `make api` |
| Chạy frontend (`:5173`, proxy sang `:8080`) | `./ops/scripts/web.ps1` | `make web` |
| Lint toàn bộ | `./ops/scripts/lint.ps1` | `make lint` |
| Test toàn bộ | `./ops/scripts/test.ps1` | `make test` |
| Tạo tài khoản | `./ops/scripts/create-user.ps1 -Email … -Name "…" -Role member -Temp` | `make create-user EMAIL=… NAME=… PASSWORD=…` |

Chi tiết từng phần:

```bash
# Frontend (cd web)
npm ci
npm run dev            # cần backend
npm run dev:mock       # API giả MSW, không cần backend; ?mock=<answered|not_found|partial|interrupted|timeout|llm_error|empty_workspace|processing_only|all_failed|near_limit>
npm run lint && npm run typecheck && npm test
npm run build && npm run size      # ngân sách: JS khởi tạo ≤ 200 KB gzip, CSS ≤ 40 KB gzip
npm run gen:api        # sinh lại src/shared/api/schema.ts từ contracts/openapi.yaml

# Backend (cd api)
./mvnw -B verify            # Spotless + Checkstyle + SpotBugs + JUnit + Testcontainers (cần Docker)
./mvnw spotless:apply       # tự sửa định dạng Java
./mvnw -B spring-boot:run

# Hợp đồng API
npx @redocly/cli@1.25.11 lint contracts/openapi.yaml

# E2E (cài `npm ci` ở gốc repo; chạy từ e2e/, mặc định trỏ vào web chạy bằng dev:mock)
cd e2e
$env:TC_ID="TC-AUTH-05"; $env:REPORT_DIR="test_report/TC-AUTH-05/round-2"
npx playwright test tests/auth/TC-AUTH-05.spec.ts --project=chromium
```

**Trước khi tạo PR phải xanh:** `lint` + `test` + `npm run build` + `npm run size`, và `gen:api` không làm đổi `schema.ts`. CI (`.github/workflows/ci.yml`) chạy 3 job bắt buộc: **web**, **api**, **contract**; `npm audit` và OWASP dependency-check chỉ cảnh báo.

**Cấu hình** nằm ở `.env` (sao từ `.env.example`, **không commit**): `LLM_PROVIDER` / `EMBEDDING_PROVIDER` (`fake` | thật: Anthropic / Ollama `bge-m3`), `EMBEDDING_BASE_URL`, `DB_PASSWORD`, `STORAGE_DIR`, `MAX_FILE_MB`, `PROCESSING_WORKERS`, `SESSION_IDLE_HOURS`. Mặc định dev dùng `LLM_PROVIDER=fake`; nếu không có Ollama thì `EMBEDDING_PROVIDER=fake`.

**Môi trường máy dev (Windows):** ổ C: gần đầy, Docker nằm ở D: — đặt cache/artifact lớn (Playwright, Testcontainers, Maven) trên D:. Embedding `bge-m3` chạy CPU chậm gấp ~4 lần ngưỡng TH-03: **không kết luận hiệu năng xử lý tài liệu từ máy dev**.

## 3. Kiến trúc

```
Trình duyệt (Vue 3 SPA) ──REST · SSE · multipart──> Backend (Spring Boot, 1 instance)
                                                      ├─ PostgreSQL 16 + pgvector (dữ liệu + vector, Flyway)
                                                      ├─ LocalFileStorage (PDF gốc, mã hóa do hạ tầng)
                                                      └─ chỉ backend gọi LLM / embedding đã được phê duyệt
```

- **Frontend** (`web/`): Vue 3 + TypeScript + Vite, Pinia, Vue Router, TanStack Query, Reka UI + Tailwind, `pdfjs-dist` (nạp lười), `markdown-it` + DOMPurify, vue-i18n. Cấu trúc `app/`, `features/<tên>/`, `shared/{api,realtime,lib,ui}`.
- **Backend** (`api/`, gói `com.chatvsdoc.api`): `config/`, `common/` (lỗi, filter), `domain/` (entity + repository), `service/{auth,documents,pdf,retrieval,answer,chat,events,analytics,audit}`, `port/` (`StoragePort`, `EmbeddingPort`, `LlmPort` + `adapter/` + `fake/`), `web/` (controller), `cli/`.
- **Hợp đồng API-first:** `contracts/openapi.yaml` là nguồn sự thật. Sửa hợp đồng **trước**, rồi sinh lại kiểu FE; `ContractTest` kiểm mọi route có trong hợp đồng.
- **Luồng lõi:** tải lên (kiểm tra + giữ chỗ nguyên tử tối đa 20) → xử lý nền (PDFBox từng trang → chia đoạn 800/1000 ký tự, chồng 100 → embedding 1024 chiều → `ready`/`failed` + sự kiện SSE) → hỏi đáp (embedding câu hỏi → top-k **mọi** tài liệu sẵn sàng → LLM với chỉ dẫn "chỉ dùng nguồn, đánh dấu `[n]`, không có căn cứ thì `NOT_FOUND`" → SSE `meta/status/delta/citation/done/error`).
- **Giả định một instance** (event bus và hàng đợi xử lý PDF chạy trong tiến trình). Muốn chạy nhiều instance phải đổi sang `LISTEN/NOTIFY` sau giao diện `EventBus`.
- **Kiểm thử không phụ thuộc LLM thật:** mọi test tự động dùng `FakeLlm`/`FakeEmbedding` (kịch bản `ngat`, `cham`, `loi dich vu`, `injection`…). LLM thật chỉ dùng khi đánh giá chất lượng AI (DEP-002) và kiểm thử S-06.
- **Mô hình dữ liệu:** `users`, `documents`, `chunks` (trang, offset, văn bản, vector), `conversations`, `messages`, `citations`, `audit_logs` (chỉ thêm, trigger CSDL chặn UPDATE/DELETE).

## 4. Quy ước

**Ngôn ngữ:** tài liệu, thông điệp giao diện và lỗi hiển thị cho người dùng bằng **tiếng Việt**; định danh trong mã (lớp, hàm, biến, bảng, cột) bằng **tiếng Anh**. Thông điệp người dùng thấy phải **khớp nguyên văn** PRD §4.3/§5.2 và SDD (test so khớp nguyên văn, không kiểm "chứa từ khóa").

**API:**
- Lỗi luôn dạng `{"error":{"code","message_key","details"}}`; ném `AppException(ErrorCode…)`; không đưa nội dung nghiệp vụ vào `details`.
- JSON `snake_case`, thời gian ISO 8601 UTC, trường lạ bị từ chối (422).
- `POST/PUT/PATCH/DELETE` phải có header `X-Requested-With: fetch`, thiếu → 403. Mọi endpoint (trừ `/auth/login`, `/healthz`) trả 401 khi chưa đăng nhập.
- Phiên: cookie `sid` HttpOnly; Secure; SameSite=Strict, hết hạn khi rảnh 8 giờ; sai mật khẩu 5 lần trong 15 phút → khóa 15 phút.

**CSDL:** đổi lược đồ bằng migration Flyway mới `V<n>__mo_ta.sql`; ghi cách hoàn tác ở đầu file vì không có downgrade. `EMBEDDING_DIM` cố định từ V1.

**Log:** JSON, mỗi dòng có `request_id`; chỉ ghi method, route (không query string), status, `duration_ms`.

**Frontend (ESLint ép, R-1…R-6):**

| Luật | Nội dung |
|---|---|
| R-1 | `shared/*` không import `features/*` hoặc `app` |
| R-2 | Feature chỉ import feature khác qua `features/<tên>/index.ts` |
| R-3 | Chỉ `shared/api` gọi `fetch`; chỉ `shared/realtime` dùng `EventSource`; XHR chỉ ở `shared/api` và `features/documents/upload` |
| R-4 | Cấm `v-html`, `innerHTML` ngoài `shared/lib/markdown` |
| R-5 | `pdfjs-dist` chỉ `import()` động trong `features/source-viewer` |
| R-6 | Chỉ `shared/lib/analytics` gửi analytics |

**Truy vết:** dùng đúng ID của tài liệu nguồn (BR, US, AC, TH, NFR, EV, D, OQ, T, TC, G) trong tên task, commit/PR, test và báo cáo. Giá trị chưa được duyệt ghi `[Đề xuất]`, thông tin thiếu ghi `[TBD: OQ-xxx]`.

**Nhánh & PR:** nhánh tích hợp `dev/phase1`; mỗi task một nhánh `task/Txx-ten-ngan` → PR vào `dev/phase1`, **cần ít nhất một người duyệt**. `dev/phase1` → `main` khi phát hành. Check `api` là required check trên `main`.

**Test case & bằng chứng:** mỗi ca là một file ở [test/test_cases/](test/test_cases/README.md) (mã `TC-<nhóm>-nn`), kết quả ở `test_report/<TC_ID>/` (`report.md`, `result.json`, `screenshots/`, `video/`; chạy lại thì thêm `round-n/`). Spec Playwright ở `e2e/tests/<nhóm>/TC-….spec.ts`.

## 5. Workflow chung

1. **Làm theo task:** đọc file `docs/implement/Txx_*.md` (đặc tả, tiêu chí hoàn thành, cách kiểm thử) trước khi code. Không làm việc ngoài phạm vi task.
2. **Hợp đồng trước, mã sau:** FE dùng MSW theo `openapi.yaml`, BE dùng hợp đồng làm test. Không để FE và BE tích hợp muộn.
3. **Lát cắt dọc:** mỗi giai đoạn kết thúc bằng một luồng chạy được end-to-end; đừng dồn tích hợp về cuối.
4. **Đo từ đầu:** ghi sự kiện EV-xxx ngay khi làm tính năng; tên và thuộc tính sự kiện chỉ nằm trong danh sách trắng `AnalyticsSchema`; không chứa nội dung câu hỏi/trả lời/tài liệu.
5. **Chất lượng AI là tính năng:** mỗi thay đổi prompt (T13) hoặc tham số truy hồi (T12: `TOP_K`, `MIN_SCORE`) kèm báo cáo chạy lại bộ DEP-002 trong PR; không chỉ tiêu nào giảm quá 2 điểm %. Kết quả LLM không xác định: chạy ≥ 3 lần, báo cáo trung bình.
6. **Viết test cùng task:** unit/component kèm mỗi task; luật kiến trúc có ca vi phạm trong `web/src/__lint_fixtures__/`; adapter mới phải qua bộ test hợp đồng `port/*Contract`.
7. **Trước khi báo "xong":** chạy `lint` + `test` (+ build/size với FE) và báo trung thực kết quả. Test đỏ thì nói rõ, không che.
8. **Cập nhật tài liệu cùng thay đổi:** đổi phạm vi/hợp đồng thì cập nhật Test Plan, task liên quan và README.
9. **Tiến độ (tính tới 2026-10-28):** T01–T16 đã triển khai; T17–T23 chưa. Xem trạng thái hiện hành ở [implement/README.md](implement/README.md) và `git log`.

## 6. Điều cấm

**Dữ liệu & bảo mật**
- **Cấm** ghi nội dung nghiệp vụ (câu hỏi, câu trả lời, văn bản PDF, tên file thật nếu nhạy cảm) vào log, analytics, công cụ báo lỗi hay `details` của lỗi; **cấm** log request/response body.
- **Cấm** đưa PDF thật chứa dữ liệu nhạy cảm vào staging hoặc gửi tới LLM bên ngoài **trước khi** An toàn thông tin và Pháp chế ký (G-08, D-006). Trước đó chỉ dùng corpus đã ẩn danh hoặc bản giả.
- **Cấm** commit `.env`, khóa API, mật khẩu, tệp trong `storage/`, PDF thật; **cấm** in giá trị bí mật ra terminal/log/PR.
- **Cấm** lưu `token`/`sid` trong `localStorage`; **cấm** render nội dung LLM/PDF theo đường nào khác ngoài `shared/lib/markdown` (không `v-html`, `innerHTML`).
- **Cấm** gửi `temperature`, `top_p`, `top_k` tới Anthropic Messages API (trả 400).
- **Cấm** để Admin hay bất kỳ ai xem hội thoại của người khác qua giao diện/API.
- **Cấm** để workspace vượt 20 tài liệu; giữ chỗ phải nguyên tử (`pg_advisory_xact_lock`).

**Hành vi AI**
- **Cấm** để mô hình dùng kiến thức ngoài tài liệu để trả lời (AC-023.2); không có căn cứ phải trả `NOT_FOUND`. **Cấm** bịa trích dẫn, hoặc hiển thị chip `[n]` bấm được khi chưa có dữ liệu trích dẫn.
- **Cấm** để chỉ thị nằm trong nội dung PDF thay đổi hành vi hệ thống (NFR-S05).

**Mã & quy trình**
- **Cấm** sửa migration Flyway đã chạy; **cấm** đổi số chiều embedding mà không tạo lại cột và tính lại vector.
- **Cấm** gọi `fetch`/`EventSource` ngoài chỗ cho phép (R-3), mã màu cứng ngoài `shared/ui/tokens.css`, import Reka UI ngoài `shared/ui`.
- **Cấm** hạ ngưỡng (TH-xx, độ phủ ≥ 70%, ngân sách bundle), tắt/nới luật lint, xóa hoặc làm yếu test để pipeline xanh. Chưa đạt thì ghi điểm nghẽn và đề xuất.
- **Cấm** `Thread.sleep`/`waitForTimeout` làm cơ chế chờ trong test; chờ theo sự kiện.
- **Cấm** hạ Testcontainers xuống dưới 1.21.4 (Docker 29 không chạy được bản 1.19).
- **Cấm** mở rộng phạm vi: OCR, định dạng khác PDF, đồng bộ kho tài liệu, ứng dụng di động, hỗ trợ Firefox/Safari (BR-023/024, CON-002/003).

## 7. Giới hạn quyền

### 7.1 Quyền người dùng trong sản phẩm

Vai trò hệ thống: `MEMBER`, `LEAD` (Trưởng nhóm), `ADMIN`. Kiểm tra bằng `@PreAuthorize` ở API, **không chỉ ở UI**.

| Hành động | Member | Lead | Admin |
|---|---|---|---|
| Đăng nhập, đổi mật khẩu của mình | ✔ | ✔ | ✔ |
| Xem danh sách tài liệu workspace, tải lên PDF, hỏi đáp | ✔ | ✔ | ✔ |
| Xem hội thoại của **chính mình** | ✔ | ✔ | ✔ (chỉ của mình) |
| Xóa tài liệu (P1, ngoài lát cắt hiện tại) | chỉ tài liệu mình tải | chưa quy định trong PRD | mọi tài liệu |
| Tạo / vô hiệu hóa tài khoản, đặt lại mật khẩu | ✘ (403) | ✘ | ✔ |
| Xem báo cáo sử dụng (chỉ số tổng hợp, không có nội dung) | ✘ (403) | ✔ **[TBD: OQ-021]** | ✔ |
| Xem nhật ký kiểm toán | ✘ | ✘ | ✔ |

Tài khoản mới: mật khẩu tạm, lần đăng nhập đầu **bắt buộc đổi** (≥ 10 ký tự, có chữ và số); trước khi đổi, mọi `/api/**` trừ `/api/me`, đăng xuất, đổi mật khẩu trả `403 PASSWORD_CHANGE_REQUIRED`. Tài liệu thuộc workspace, không thuộc cá nhân: vô hiệu hóa người tải lên không làm mất tài liệu.

### 7.2 Quyền của AI agent khi làm việc trong repo **[Đề xuất]**

Mặc định theo allow-list trong `.claude/settings.json`; việc không có trong đó thì hỏi trước.

- **Được làm tự do:** đọc mã/tài liệu, chạy `lint`/`test`/`build`/`verify`, sửa mã trong phạm vi task, tạo nháp tài liệu trong `docs/`.
- **Hỏi trước khi làm:** xóa file/thư mục, đổi `contracts/openapi.yaml` (ảnh hưởng cả FE lẫn BE), tạo migration Flyway, thêm/nâng phụ thuộc, đổi cấu hình CI/luật lint, chạy lệnh gọi ra Internet hoặc LLM thật (tốn chi phí, G-08), cài phần mềm toàn cục.
- **Không tự làm:** `git commit`/`push`/`merge`/`rebase`/`reset --hard`, force-push, đẩy lên `main`, tạo hoặc merge PR, sửa branch protection — chỉ làm khi người dùng yêu cầu rõ trong lượt đó. Không `--no-verify`, không bỏ qua hook.
- **Không bao giờ:** đọc rồi in nội dung `.env` hay khóa bí mật; đưa dữ liệu thật vào dịch vụ bên ngoài; đóng vai người ký G-xx (chữ ký An toàn thông tin/Pháp chế/PO là của con người).
- **Không tự quyết các vấn đề còn mở** — nêu ra và hỏi: OQ-016 (AI có dùng kiến thức ngoài tài liệu không; PRD đang giả định "không"), OQ-021 (ai là "Trưởng nhóm"), OQ-UX-01 (backend có trả vị trí đoạn trích không), OQ-UX-02 (proxy có đệm SSE không), OQ-011 (ngân sách/thời hạn), lựa chọn nhà cung cấp LLM.

## 8. Tài liệu tham chiếu nhanh

| Cần biết | Xem |
|---|---|
| Vì sao làm, mục tiêu, phạm vi, ràng buộc | [requirements/BRD.md](requirements/BRD.md) |
| Yêu cầu chi tiết, AC, ngưỡng TH-xx, NFR, cổng launch G-01…G-14, sự kiện EV-xxx | [requirements/PRD.md](requirements/PRD.md) |
| Chiến lược kiểm thử, tiêu chí đạt/dừng, rủi ro | [test/Test_Plan.md](test/Test_Plan.md) |
| Test case từng ca | [test/test_cases/](test/test_cases/README.md) |
| Lộ trình, kiến trúc, 23 task | [implement/implement_plan.md](implement/implement_plan.md), [implement/README.md](implement/README.md) |
| Giao diện, thuật toán ALG-01/02/03 | [design/SDD_UIUX_Blueprint.md](design/SDD_UIUX_Blueprint.md) |
| User story xương sống BB-01/BB-02 | [user_stories/US_Backbone_Upload_va_HoiDap.md](user_stories/US_Backbone_Upload_va_HoiDap.md) |
| Quy ước backend chi tiết | [../api/README.md](../api/README.md) |
