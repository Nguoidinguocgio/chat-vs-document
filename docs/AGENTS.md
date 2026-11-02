# AGENTS.md

Dự án "Chat với tài liệu" (MVP): tải PDF lên một workspace chung, hỏi đáp tiếng Việt/Anh, câu trả lời có trích dẫn tên tài liệu + số trang; không có căn cứ thì từ chối chứ không bịa.
Nguồn sự thật: `docs/requirements/` (BRD, PRD), `docs/design/`, `docs/implement/` (kế hoạch + task T01–T23), `contracts/openapi.yaml`. Tài liệu chưa rõ hoặc mâu thuẫn → **dừng và hỏi**, không đoán.
Chi tiết kiến trúc, quy ước đầy đủ, bảng quyền người dùng: [CLAUDE.md](CLAUDE.md). File này là bản rút gọn cho agent; hai file lệch nhau thì tin mã nguồn/CI rồi sửa cả hai.

## Stack
- **Backend** (`api/`): Java 21, Spring Boot 3.3, Maven (`./mvnw`), Flyway, JPA, Apache PDFBox, Argon2. Kiểm tra: JUnit 5 + Testcontainers, Spotless, Checkstyle, SpotBugs.
- **Frontend** (`web/`): Vue 3, TypeScript, Vite, Pinia, Vue Router, TanStack Query, Reka UI + Tailwind, `pdfjs-dist`, `markdown-it` + DOMPurify, vue-i18n. Kiểm tra: ESLint, `vue-tsc`, Vitest, size-limit; API giả MSW.
- **CSDL**: PostgreSQL 16 + pgvector (+ `citext`), một CSDL cho dữ liệu và vector (`EMBEDDING_DIM=1024`).
- **AI**: embedding `bge-m3` qua Ollama; LLM Anthropic `claude-sonnet-5-5` (T13). Chỉ backend được gọi, qua `port/` (`EmbeddingPort`, `LlmPort`, `StoragePort`). Dev và test chạy bằng bản giả `FakeEmbedding`/`FakeLlm`, chọn bằng `EMBEDDING_PROVIDER` / `LLM_PROVIDER`.
- **E2E**: Playwright + axe-core (`e2e/`). Hợp đồng API: `contracts/openapi.yaml` (Redocly lint).

## Lệnh
| Việc | Windows (PowerShell, tại gốc repo) | Git Bash / Linux / macOS |
|---|---|---|
| CSDL dev (Docker) | `./ops/scripts/up.ps1` | `make up` / `make down` |
| Chạy API (`:8080`) | `./ops/scripts/api.ps1` | `make api` |
| Chạy web (`:5173`, proxy `/api`) | `./ops/scripts/web.ps1` | `make web` |
| Web không cần backend (MSW) | `cd web; npm run dev:mock` | như bên trái |
| Lint | `./ops/scripts/lint.ps1` | `make lint` |
| Test | `./ops/scripts/test.ps1` | `make test` |
| Tạo tài khoản | `./ops/scripts/create-user.ps1 -Email … -Name "…" -Role member -Temp` | `make create-user EMAIL=… NAME=… PASSWORD=…` |
| Sinh kiểu FE từ hợp đồng | `cd web; npm run gen:api` | như bên trái |
| Sửa định dạng Java | `cd api; ./mvnw spotless:apply` | như bên trái |
| Lint hợp đồng | `npx @redocly/cli@1.25.11 lint contracts/openapi.yaml` | như bên trái |
| E2E một ca | `cd e2e; $env:TC_ID="TC-…"; npx playwright test tests/<nhóm>/TC-….spec.ts --project=chromium` | — |

**Cổng chất lượng** (khớp CI `web` + `api` + `contract`): `lint` + `test` + `cd web && npm run build && npm run size`, và `npm run gen:api` không làm đổi `web/src/shared/api/schema.ts`. Test backend cần Docker đang chạy (Testcontainers ≥ 1.21.4).
Sao chép `.env.example` thành `.env` rồi điền giá trị (không commit). Không có Ollama thì đặt `EMBEDDING_PROVIDER=fake`; mặc định dev `LLM_PROVIDER=fake`.

## Cấu trúc
```
web/        Vue 3 SPA: app/, features/<tên>/, shared/{api,realtime,lib,ui}
api/        Spring Boot, gói com.chatvsdoc.api: config, common, domain, service/*, port (adapter, fake), web, cli
contracts/  openapi.yaml — nguồn sự thật của API
e2e/        Playwright (tests/<nhóm>/TC-*.spec.ts, helpers/)
ops/        docker-compose.yml, db/init.sql, scripts/*.ps1
test_report/  bằng chứng chạy test theo TC_ID (report.md, result.json, screenshots/, video/)
docs/       requirements/ · design/ · implement/ · test/ · user_stories/
```
Phụ thuộc backend: `web` (controller) → `service` → `port` → `adapter`/`fake`. Dịch vụ chỉ dùng `LlmPort`/`EmbeddingPort`/`StoragePort`, không gọi nhà cung cấp AI hay `adapter` cụ thể trực tiếp (embedding dùng ở `service/pdf` và `service/retrieval`, LLM ở `service/answer`).
Phụ thuộc frontend (ESLint ép, R-1…R-6): `shared/*` không import `features/*`/`app`; feature chỉ import feature khác qua `index.ts`; chỉ `shared/api` gọi `fetch`, chỉ `shared/realtime` dùng `EventSource`; nội dung LLM/PDF chỉ render qua `shared/lib/markdown`; `pdfjs-dist` chỉ `import()` động trong `features/source-viewer`.

## Quy ước code
- Mã lỗi, đường dẫn API, tên sự kiện SSE, tên trường JSON: **đúng từng chữ** với `contracts/openapi.yaml`. Không tự thêm mã lỗi hay trường mới; cần thêm thì sửa hợp đồng trước (xin phép), rồi `gen:api`.
- JSON `snake_case`, thời gian ISO 8601 UTC, ID UUID v4; Java/TS theo quy ước ngôn ngữ (camelCase/PascalCase). Lỗi API luôn `{"error":{"code","message_key","details"}}`; FE chỉ tra thông điệp theo `message_key`.
- POST/PUT/PATCH/DELETE phải có `X-Requested-With: fetch`. Định danh trong mã bằng tiếng Anh; tài liệu và thông điệp người dùng bằng tiếng Việt, khớp nguyên văn PRD §4.3/§5.2 và SDD §3.5.3.
- Đổi lược đồ CSDL = migration Flyway mới `V<n>__mo_ta.sql`, không sửa migration đã chạy.
- Hàm phụ thuộc thời gian nhận `Clock`/`now` để test được.
- Commit nhỏ, một task một commit, kèm mã truy vết **[Đề xuất]**: `feat(documents): upload API (T07, US-010, BR-001)`. Nhánh `task/Txx-ten-ngan` → PR vào `dev/phase1`.
- Test viết trước hoặc cùng lúc với mã; mỗi mục "Tiêu chí hoàn thành"/"Cách kiểm thử" trong file `docs/implement/Txx_*.md` thành một test. Test AI/E2E dùng bản giả, so khớp nguyên văn thông điệp.

## Điều cấm
- Không ghi bí mật vào mã, log, ảnh container, dữ liệu thử; không commit `.env`, `storage/`, PDF thật. Không ghi nội dung câu hỏi, câu trả lời, văn bản tài liệu vào log, analytics, `details` của lỗi.
- Không gửi PDF thật hoặc gọi LLM thật khi chưa có chữ ký An toàn thông tin/Pháp chế (G-08); dùng bản giả hoặc corpus đã ẩn danh.
- Không gửi `temperature`/`top_p`/`top_k` tới Anthropic Messages API (trả 400).
- Không thêm thư viện ngoài `api/pom.xml` / `web/package.json` mà chưa hỏi.
- Không làm các mục `[Đề xuất]` / `[TBD]` chưa được duyệt như yêu cầu chắc chắn; không tự trả lời OQ-016, OQ-021, OQ-UX-01/02, OQ-011.
- Không tự đổi `contracts/openapi.yaml` hay ngưỡng/AC của PRD để mã chạy được: dừng và hỏi.
- Không hạ ngưỡng, nới luật lint, xóa hay làm yếu test để pipeline xanh; không bỏ qua test hỏng.
- Không mở rộng phạm vi: OCR, định dạng khác PDF, đồng bộ kho tài liệu, di động, Firefox/Safari.
- Không `git commit`, `git push`, `--force`, `reset --hard`, `--no-verify` khi người dùng chưa yêu cầu rõ trong lượt đó; không đẩy lên `main`, không tạo/merge PR.
- Không dùng dịch vụ cloud (Artifact/Docs/Drive): ghi file cục bộ.

## Quyền hạn
- **Được sửa theo task:** `web/`, `api/`, `e2e/`, `ops/`, `.github/`, `Makefile`, `test_report/`, `docs/implement/` (trạng thái, mục "Ghi chú"), `docs/test/` (test case, kết quả), `AGENTS.md`, `docs/CLAUDE.md`.
- **Hỏi trước:** `contracts/openapi.yaml`, migration Flyway, `pom.xml`/`package.json`, CI và luật lint, cấu hình `.claude/`, xóa file/thư mục.
- **Chỉ sửa khi người dùng yêu cầu:** `docs/requirements/` (BRD, PRD và checklist), `docs/design/`, `docs/user_stories/`. Cần đổi → nêu câu hỏi và chờ người duyệt, vì đây là nguồn yêu cầu và truy vết.
- Lưu ý: `.claude/settings.json` hiện chỉ có allow-list, **không chặn ghi** thư mục nào; các giới hạn trên phải tự giác tuân thủ.

## Vòng lặp làm việc
1. **Hiểu:** đọc `docs/implement/Txx_*.md` + US/AC/PRD liên quan. AC chưa rõ hoặc có câu hỏi mở chặn task → dừng và hỏi.
2. **Hợp đồng:** nếu task đổi API, đề xuất thay đổi `openapi.yaml` trước.
3. **Code:** một task, chỉ phạm vi ghi trong task, giữ nguyên chữ ký đã có trong hợp đồng.
4. **Test:** viết test, chạy cổng chất lượng, đọc kết quả thật; báo trung thực kể cả khi đỏ.
5. **Rà soát:** tự đọc lại diff (hoặc `/code-review`) đối chiếu DoD trong `docs/implement/README.md` §4.
6. **Dừng** khi lint, kiểu, test, build xanh và tài liệu liên quan (README, OpenAPI, trạng thái task) đã cập nhật. Bị chặn hoặc cần quyết định → dừng và hỏi; tối đa 3 vòng sửa cho một task.

## Quyết định kiến trúc và truy vết
- Chưa có thư mục ADR. Quyết định kiến trúc mới (đổi cấu trúc, thư viện, hợp đồng, hành vi khác tài liệu thiết kế) → nêu rõ cho người dùng và ghi vào mục "Ghi chú" của file task tương ứng sau khi được duyệt.
- Truy vết qua ID (BR, US, AC, TH, NFR, EV, T, TC, G) trong commit, PR, tên test và `test_report/`; ma trận yêu cầu → ca kiểm thử nằm ở `docs/test/Test_Plan.md` §6.2.
