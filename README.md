# Chat với tài liệu

Hỏi đáp trên tài liệu PDF nội bộ, câu trả lời có trích dẫn trang nguồn.

| Thư mục | Nội dung |
|---|---|
| `web/` | Frontend Vue 3 + TypeScript (Vite) — [luật kiến trúc](#luật-kiến-trúc-frontend) |
| `api/` | Backend Spring Boot 3, Java 21, Maven — xem [api/README.md](api/README.md) |
| `ops/` | `docker-compose.yml`, `db/init.sql`, script `scripts/*.ps1` |
| `contracts/` | `openapi.yaml` (từ T06) |
| `docs/` | BRD, PRD, SDD, user story, [danh sách task](docs/implement/README.md) |

## Chạy lần đầu (Windows, ~15 phút)

**1. Cài một lần**
- [Node.js 20 LTS](https://nodejs.org) trở lên
- JDK 21 (Temurin hoặc Microsoft Build of OpenJDK). Script tự tìm JDK 21 trong `C:\Program Files\...` / `D:\Program Files\...` nếu `JAVA_HOME` chưa trỏ đúng.
- Maven: không bắt buộc — dùng Maven Wrapper `api/mvnw.cmd` có sẵn (script ưu tiên `mvn` trên PATH nếu có, cần ≥ 3.6.3)
- CSDL PostgreSQL 16 + pgvector, chọn **một** trong hai cách:
  - **(a) Docker Desktop** (khuyến nghị) — không cần cài gì thêm.
  - **(b) Cài trực tiếp**: PostgreSQL 16 + [pgvector cho Windows](https://github.com/pgvector/pgvector#windows), rồi chạy `psql -U postgres -f ops/db/init.sql`.

**2. Cấu hình**
```powershell
Copy-Item .env.example .env   # sửa DB_PASSWORD nếu cần; .env không được commit
```
Mặc định `EMBEDDING_PROVIDER=ollama` (cần [Ollama](https://ollama.com) chạy ở `localhost:11434` với `ollama pull bge-m3`) và `LLM_PROVIDER=fake`. Không có Ollama thì đặt `EMBEDDING_PROVIDER=fake`; tài liệu vẫn được xử lý bằng vector giả, đủ để thử giao diện.

**3. Chạy** (PowerShell, tại thư mục gốc repo)
```powershell
./ops/scripts/up.ps1     # (a) bật PostgreSQL bằng Docker và chờ sẵn sàng — bỏ qua nếu dùng cách (b)
./ops/scripts/api.ps1    # terminal 1: backend http://localhost:8080/healthz → {"status":"ok"}
./ops/scripts/web.ps1    # terminal 2: frontend http://localhost:5173 (proxy /api, /healthz sang :8080)
```
Lần đầu backend tự chạy migration Flyway (`api/src/main/resources/db/migration`). Thư viện UI: http://localhost:5173/dev/ui (chỉ ở chế độ dev).

**4. Tạo tài khoản rồi đăng nhập** tại http://localhost:5173 (chưa đăng nhập sẽ được chuyển tới `/login`)
```powershell
./ops/scripts/create-user.ps1 -Email ban@congty.vn -Name "Nguyễn Văn A" -Role member -Temp
# không truyền -Password thì script hỏi mật khẩu (không lưu vào lịch sử shell)
# -Temp: lần đăng nhập đầu bắt buộc đổi mật khẩu (≥ 10 ký tự, có chữ và số)
```

**Chạy frontend không cần backend** (API giả MSW theo `contracts/openapi.yaml`):
```powershell
cd web; npm run dev:mock     # http://localhost:5173 — thêm ?mock=<kịch bản> để đổi dữ liệu
```
| Tài khoản giả (mật khẩu `Matkhau-2026`) | Hành vi |
|---|---|
| `lan@congty.vn` | Thành viên, vào thẳng workspace |
| `admin@congty.vn` | Admin |
| `moi@congty.vn` | Mật khẩu tạm → bắt buộc đổi mật khẩu |
| `khoa@congty.vn` (mật khẩu bất kỳ) | Bị khóa tạm (423) |
| `vohieu@congty.vn` | Tài khoản bị vô hiệu hóa |

Kịch bản `?mock=`: `answered` (mặc định), `not_found`, `partial`, `interrupted`, `timeout`, `llm_error`, `empty_workspace`, `processing_only`, `all_failed`, `near_limit`.

Lỗi thường gặp:
| Triệu chứng | Cách xử lý |
|---|---|
| `Không tìm thấy JDK 21+` | Cài JDK 21, đặt `JAVA_HOME` |
| Backend báo `Connection refused` cổng 5432 | Chưa chạy `up.ps1` / dịch vụ PostgreSQL; hoặc cổng 5432 đang bị PostgreSQL khác chiếm |
| `running scripts is disabled` | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| Test backend lỗi Docker | Testcontainers cần Docker Desktop đang chạy |

Linux/macOS/Git Bash: `make up`, `make api`, `make web`, `make test`, `make lint`, `make create-user EMAIL=… NAME=… PASSWORD=…`.

## Kiểm tra trước khi tạo PR
```powershell
./ops/scripts/lint.ps1   # ESLint + vue-tsc + Spotless + Checkstyle
./ops/scripts/test.ps1   # Vitest + ./mvnw verify (JUnit + Testcontainers + SpotBugs)
cd web; npm run build; npm run size   # ngân sách bundle: JS khởi tạo ≤ 200 KB gzip, CSS ≤ 40 KB gzip
```
Sửa định dạng Java: `cd api; ./mvnw spotless:apply`.

**Hợp đồng API**: sửa `contracts/openapi.yaml` **trước**, rồi `cd web; npm run gen:api` để sinh lại `web/src/shared/api/schema.ts` (CI báo lỗi nếu file sinh ra lệch hợp đồng). Lint hợp đồng: `npx @redocly/cli@1.25.11 lint contracts/openapi.yaml`.

CI (`.github/workflows/ci.yml`) chạy trên mỗi PR: **web** (lint, typecheck, test, build, size), **api** (`./mvnw verify`), **contract** (lint `contracts/openapi.yaml` khi có). Quét phụ thuộc (`npm audit`, OWASP dependency-check) chỉ cảnh báo. Bật *Require status checks* cho `web`, `api`, `contract` ở cài đặt nhánh `dev/phase1` và `main` để chặn merge khi đỏ.

## Quy ước nhánh
- Nhánh tích hợp: `dev/phase1`. Mỗi task một nhánh `task/Txx-ten-ngan` (ví dụ `task/T04-auth-session`) → PR vào `dev/phase1`, cần ít nhất một người duyệt.
- `dev/phase1` → `main` khi phát hành.

## Luật kiến trúc frontend
Ép bằng ESLint (`web/eslint.config.js`); mỗi luật có ca vi phạm trong `web/src/__lint_fixtures__/` và test khẳng định luật báo lỗi.

| Luật | Nội dung |
|---|---|
| R-1 | `shared/*` không import `features/*` hoặc `app` |
| R-2 | Feature chỉ import feature khác qua `features/<tên>/index.ts` |
| R-3 | Chỉ `shared/api` gọi `fetch`; chỉ `shared/realtime` dùng `EventSource`; XHR chỉ ở `shared/api` và `features/documents/upload` |
| R-4 | Cấm `v-html`, `innerHTML` ngoài `shared/lib/markdown` |
| R-5 | `pdfjs-dist` chỉ được `import()` động trong `features/source-viewer` |
| R-6 | Chỉ `shared/lib/analytics` gửi analytics |
| — | Không lưu `token`/`sid` trong `localStorage`; không mã màu cứng ngoài `shared/ui/tokens.css`; không import Reka UI ngoài `shared/ui` |
