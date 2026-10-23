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
- Vite + React 18 + TypeScript `strict: true`, alias `@/` → `src/`.
- Thư mục theo SDD 3.1: `src/app`, `src/features/{auth,documents,chat,source-viewer,conversations}`, `src/shared/{ui,api,realtime,lib}`, `src/locales/vi`, `src/mocks`. Mỗi feature có `index.ts` là API công khai.
- Cài: `@tanstack/react-query`, `zustand`, `react-router-dom`, `@radix-ui/*`, `tailwindcss`, `react-markdown`, `rehype-sanitize`, `pdfjs-dist`, `i18next`; dev: `vitest`, `@testing-library/react`, `msw`, `eslint`, `eslint-plugin-boundaries`, `size-limit`, `openapi-typescript`.
- Script: `dev`, `build`, `test`, `lint`, `typecheck`, `size`.

### 3.3 Luật ép bằng ESLint (bắt buộc)
| Luật SDD | Cách ép |
|---|---|
| R-1 `shared/*` không import `features/*` hoặc `app` | `boundaries/element-types` |
| R-2 `features/X` không import nội bộ `features/Y` | `boundaries/element-types` + chỉ cho import `features/*/index.ts` |
| R-3 chỉ `shared/api` gọi `fetch`, chỉ `shared/realtime` dùng `EventSource` | `no-restricted-globals` (`fetch`, `EventSource`, `XMLHttpRequest`) bật mặc định, tắt cho 2 thư mục trên và cho `features/documents/upload/**` (được dùng XHR để lấy tiến trình) |
| R-4 cấm `dangerouslySetInnerHTML`, `innerHTML` | `no-restricted-syntax` bật toàn cục; chỉ `shared/lib/markdown/**` được tắt |
| R-5 `pdfjs-dist` chỉ import động trong `features/source-viewer` | `no-restricted-imports` + cho phép `import()` trong thư mục đó |
| R-6 chỉ `shared/lib/analytics` gửi analytics | `no-restricted-imports` đối với module gửi sự kiện |
Thêm cấm lưu token: `no-restricted-properties` cho `localStorage.setItem` với khóa chứa `token`/`sid`.

### 3.4 API (`api/`)
- Python 3.12, `pyproject.toml` (FastAPI, uvicorn, SQLAlchemy 2, Alembic, psycopg, pydantic-settings, PyMuPDF, argon2-cffi, httpx; dev: pytest, pytest-asyncio, ruff, mypy).
- `app/main.py` với `GET /healthz` trả `{"status":"ok"}`; `ruff` + `mypy` chạy được.

### 3.5 Hạ tầng cục bộ (`ops/`)
- `docker-compose.yml`: PostgreSQL 16 có pgvector (image `pgvector/pgvector:pg16`), cổng 5432, volume.
- `.env.example` liệt kê đủ biến ở README; `.env` nằm trong `.gitignore`.
- `Makefile` (hoặc `justfile`): `make up`, `make api`, `make web`, `make test`, `make lint`.

### 3.6 CI (GitHub Actions)
Chạy trên mỗi PR: (1) web: `lint`, `typecheck`, `test`, `build`, `size`; (2) api: `ruff`, `mypy`, `pytest` với dịch vụ Postgres; (3) kiểm tra `contracts/openapi.yaml` hợp lệ (bổ sung ở T06). Chặn merge nếu bất kỳ job nào đỏ. Cache phụ thuộc. Quét phụ thuộc `npm audit --omit=dev` và `pip-audit` (cảnh báo, chưa chặn).

### 3.7 Kích thước bundle
`size-limit`: JS khởi tạo cho route `/c` ≤ 200 KB gzip, CSS ≤ 40 KB gzip (SDD mục 4). Chunk `pdfjs` và `source-viewer` nằm ngoài giới hạn này vì tải lười.

## 4. Tệp sẽ tạo
`web/` và `api/` như trên · `ops/docker-compose.yml` · `.github/workflows/ci.yml` · `.env.example` · `README.md` gốc (cách chạy, cấu trúc, quy ước nhánh) · `web/.eslintrc.*` · `web/.size-limit.json`.

## 5. Tiêu chí hoàn thành
- [ ] Clone mới → `make up && make api` và `make web` chạy; `GET /healthz` = 200; trang web mở được.
- [ ] CI xanh trên nhánh trống.
- [ ] Có file `web/src/__lint_fixtures__/violations.tsx` cố tình vi phạm từng luật R-1…R-6; test lint khẳng định mỗi luật **báo lỗi** (để biết luật không bị tắt nhầm). File này bị loại khỏi build.
- [ ] `size-limit` chạy trong CI và fail khi vượt ngưỡng (thử bằng cách thêm thư viện lớn tạm thời).
- [ ] README gốc hướng dẫn một người mới chạy được trong ≤ 15 phút.

## 6. Kiểm thử
Lint fixtures (trên); chạy thử CI bằng một PR mẫu có lỗi lint và PR sạch.

## 7. Ghi chú
Nếu đội chọn ngăn xếp backend khác (việc #1 ở [kế hoạch](implement_plan.md)), thay mục 3.4 và 3.6 tương ứng; các mục còn lại giữ nguyên.
