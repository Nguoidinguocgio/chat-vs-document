# T01 — Khởi tạo repo, scaffold, CI, luật ranh giới

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / Hạ tầng |
| **Ước tính** | 2 ngày |
| **Phụ thuộc** | — |
| **Mở khóa** | T02, T03, T06 |
| **Liên quan** | SDD 1.2 (ngăn xếp), 3.3 (luật R-1…R-6); G-09 (CI) |

## 1. Mục tiêu
Một repo mà mọi người clone về chạy được ngay frontend, backend và CSDL cục bộ; CI tự chạy lint + test + build; các luật kiến trúc của SDD được **ép bằng lint** để vi phạm không merge được.

## 2. Phạm vi
**Trong:** cấu trúc thư mục, scaffold web/api, docker-compose, CI, lint, cấu hình kích thước bundle, `.env.example`, README gốc.
**Ngoài:** mã nghiệp vụ; triển khai lên staging/production (T23).

## 3. Đặc tả chi tiết

### 3.1 Cấu trúc
Theo "Cấu trúc repo" ở [README](README.md). Nhánh làm việc: `dev/phase1`; mỗi task một nhánh `task/Txx-ten-ngan` rồi PR vào `dev/phase1`.

### 3.2 Web (`web/`)
- Vite + **Vue 3 (Composition API, `<script setup lang="ts">`)** + TypeScript `strict: true`, alias `@/` → `src/`. Node.js 20 LTS.
- Thư mục theo SDD 3.1: `src/app`, `src/features/{auth,documents,chat,source-viewer,conversations}`, `src/shared/{ui,api,realtime,lib}`, `src/locales/vi`, `src/mocks`. Mỗi feature có `index.ts` là API công khai.
- Cài: `vue`, `vue-router`, `pinia`, `@tanstack/vue-query`, `reka-ui` (Radix Vue; thành phần headless), `tailwindcss`, `markdown-it`, `dompurify`, `pdfjs-dist`, `vue-i18n`; dev: `vitest`, `@vue/test-utils`, `@testing-library/vue`, `msw`, `eslint`, `eslint-plugin-vue`, `vue-tsc`, `eslint-plugin-boundaries`, `size-limit`, `openapi-typescript`.
- Script: `dev`, `build`, `test`, `lint`, `typecheck` (`vue-tsc --noEmit`), `size`.

### 3.3 Luật ép bằng ESLint (bắt buộc)
| Luật SDD | Cách ép |
|---|---|
| R-1 `shared/*` không import `features/*` hoặc `app` | `boundaries/element-types` |
| R-2 `features/X` không import nội bộ `features/Y` | `boundaries/element-types` + chỉ cho import `features/*/index.ts` |
| R-3 chỉ `shared/api` gọi `fetch`, chỉ `shared/realtime` dùng `EventSource` | `no-restricted-globals` (`fetch`, `EventSource`, `XMLHttpRequest`) bật mặc định, tắt cho 2 thư mục trên và cho `features/documents/upload/**` (được dùng XHR để lấy tiến trình) |
| R-4 cấm `v-html`, `innerHTML` | `vue/no-v-html` = error + `no-restricted-syntax` cho `innerHTML` bật toàn cục; chỉ `shared/lib/markdown/**` được tắt |
| R-5 `pdfjs-dist` chỉ import động trong `features/source-viewer` | `no-restricted-imports` + cho phép `import()` trong thư mục đó |
| R-6 chỉ `shared/lib/analytics` gửi analytics | `no-restricted-imports` đối với module gửi sự kiện |
Thêm cấm lưu token: `no-restricted-properties` cho `localStorage.setItem` với khóa chứa `token`/`sid`.

### 3.4 API (`api/`)
- **Java 21 (LTS) + Spring Boot 3.x**, build bằng **Maven** (`pom.xml`, có Maven Wrapper `mvnw`). Phụ thuộc: `spring-boot-starter-web`, `-validation`, `-security`, `-data-jpa`, `-actuator`; `flyway-core` + `flyway-database-postgresql`; `postgresql` (JDBC); `pgvector` (pgvector-java); `org.apache.pdfbox:pdfbox`; `bcprov-jdk18on` (cho `Argon2PasswordEncoder`); `springdoc-openapi` (tùy chọn, đối chiếu hợp đồng); test: `spring-boot-starter-test` (JUnit 5, AssertJ, Mockito), `testcontainers` (`postgresql`), `spring-security-test`.
- Gói gốc `com.chatvsdoc.api`. `GET /healthz` (controller riêng, trả `{"status":"ok"}`); `spring-boot-starter-actuator` chỉ dùng cho `/readyz` ở T23.
- Chất lượng mã: `spotless-maven-plugin` (google-java-format), `maven-checkstyle-plugin`, `spotbugs-maven-plugin`; `./mvnw verify` chạy được.

### 3.5 Hạ tầng cục bộ (`ops/`)
- PostgreSQL 16 + extension `pgvector`, cổng 5432. Hai cách (chọn theo máy, ghi trong README gốc): (a) `ops/docker-compose.yml` dùng image `pgvector/pgvector:pg16` + volume; (b) cài PostgreSQL 16 trực tiếp trên Windows và cài pgvector, chạy `ops/db/init.sql` (tạo role/DB, `CREATE EXTENSION vector, citext`).
- `.env.example` liệt kê đủ biến ở README; `.env` nằm trong `.gitignore`. Spring đọc qua biến môi trường (`application.yml` dùng `${DB_URL}`…).
- Script chạy trên Windows: `ops/scripts/*.ps1` (PowerShell) — `up`, `api`, `web`, `test`, `lint`; kèm `Makefile` tương đương cho Linux/macOS/Git Bash.

### 3.6 CI (GitHub Actions)
Chạy trên mỗi PR: (1) web: `lint`, `typecheck`, `test`, `build`, `size`; (2) api: `./mvnw verify` (gồm Spotless/Checkstyle/SpotBugs + JUnit với Testcontainers hoặc dịch vụ Postgres `pgvector/pgvector:pg16`); (3) kiểm tra `contracts/openapi.yaml` hợp lệ (bổ sung ở T06). Chặn merge nếu bất kỳ job nào đỏ. Cache phụ thuộc (npm, `~/.m2`). Quét phụ thuộc `npm audit --omit=dev` và OWASP `dependency-check-maven` (cảnh báo, chưa chặn).

### 3.7 Kích thước bundle
`size-limit`: JS khởi tạo cho route `/c` ≤ 200 KB gzip, CSS ≤ 40 KB gzip (SDD mục 4). Chunk `pdfjs` và `source-viewer` nằm ngoài giới hạn này vì tải lười.

## 4. Tệp sẽ tạo
`web/` và `api/` (`pom.xml`, `mvnw`) như trên · `ops/docker-compose.yml` · `ops/db/init.sql` · `ops/scripts/*.ps1` · `.github/workflows/ci.yml` · `.env.example` · `README.md` gốc (cách chạy, cấu trúc, quy ước nhánh) · `web/eslint.config.*` · `web/.size-limit.json`.

## 5. Tiêu chí hoàn thành
- [ ] Clone mới → chạy `up`, `api`, `web` (script PowerShell hoặc `make`) được trên Windows; `GET /healthz` = 200; trang web mở được.
- [ ] CI xanh trên nhánh trống.
- [ ] Có file `web/src/__lint_fixtures__/violations.vue` (và `.ts`) cố tình vi phạm từng luật R-1…R-6; test lint khẳng định mỗi luật **báo lỗi** (để biết luật không bị tắt nhầm). File này bị loại khỏi build.
- [ ] `size-limit` chạy trong CI và fail khi vượt ngưỡng (thử bằng cách thêm thư viện lớn tạm thời).
- [ ] README gốc hướng dẫn một người mới chạy được trong ≤ 15 phút.

## 6. Kiểm thử
Lint fixtures (trên); chạy thử CI bằng một PR mẫu có lỗi lint và PR sạch.

## 7. Ghi chú
Ngăn xếp đã chốt: **Vue 3 + Spring Boot (Java 21) + PostgreSQL 16/pgvector** (việc #1 ở [kế hoạch](implement_plan.md)), phù hợp môi trường PC Windows hiện có.

**Ghi chú khi hiện thực (2026-10-26):**
- Lint fixtures: `web/src/__lint_fixtures__/violations.{ts,vue}` gồm các ca `// @case <tên> <đường dẫn ảo> <luật>`; `lint.test.ts` lint từng ca tại đường dẫn ảo (luật R-1/R-2/R-3 phụ thuộc thư mục) và cả các ca "ok" để bắt ngoại lệ bị mở quá rộng. Thêm luật cấm import Reka UI ngoài `shared/ui` (T02 mục 3.4).
- Luật cấm lưu token dùng `no-restricted-syntax` (khóa chứa `token`/`sid`) vì `no-restricted-properties` không xét được đối số.
- Script Windows: `ops/scripts/{up,api,web,test,lint,create-user}.ps1` (UTF-8 có BOM để PowerShell 5.1 đọc đúng tiếng Việt), dùng chung `_env.ps1` (nạp `.env`, tìm JDK 21, ưu tiên `mvn` trên PATH rồi tới `mvnw.cmd`). `.gitattributes` giữ `mvnw` ở LF cho runner Linux.
- CI: job `dependency-check` đặt `continue-on-error` (chỉ cảnh báo); cần secret `NVD_API_KEY` để chạy nhanh. Chặn merge cần bật branch protection trên GitHub (không cấu hình được từ repo).
