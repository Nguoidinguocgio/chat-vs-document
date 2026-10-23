# T03 — Backend nền tảng và lược đồ CSDL

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T01 |
| **Mở khóa** | T04 và toàn bộ backend |
| **Liên quan** | SDD 3.4 (dữ liệu), 3.5.4; NFR-S01/S02; AC-060.1 |

## 1. Mục tiêu
Khung backend có cấu trúc rõ, xử lý lỗi theo một định dạng duy nhất, CSDL có lược đồ đầy đủ cho hai chức năng chính, và các "port" để thay nhà cung cấp lưu trữ/embedding/LLM mà không sửa nghiệp vụ.

## 2. Phạm vi
**Trong:** cấu trúc dự án, cấu hình, lỗi chuẩn, middleware, migration toàn bộ bảng, CLI tạo người dùng, port + bản giả cho test.
**Ngoài:** logic xác thực (T04), logic tài liệu/hội thoại.

## 3. Đặc tả chi tiết

### 3.1 Cấu trúc `api/app/`
```
main.py            tạo FastAPI, gắn router, middleware, xử lý lỗi
core/              config.py (pydantic-settings), errors.py, security.py, logging.py
db/                session.py, models.py, migrations/ (Alembic)
routers/           auth.py, documents.py, events.py, chat.py, citations.py, analytics.py
services/          auth, documents, pdf_pipeline, retrieval, answering, events, audit, analytics
ports/             storage.py, embedding.py, llm.py  (+ adapters/ và fakes/)
cli.py             create-user, disable-user, search (T12)
```

### 3.2 Lỗi chuẩn
`AppError(code, status, message_key, details=None)`; một exception handler chuyển thành `{"error":{"code","message_key","details"}}`. Lỗi không lường trước → `500 INTERNAL_ERROR` với `message_key="errors.generic"`, ghi log đầy đủ (không kèm nội dung nghiệp vụ). Lỗi kiểm tra đầu vào của FastAPI (`RequestValidationError`) → `422 VALIDATION_ERROR` cùng định dạng.

### 3.3 Middleware
- **Request ID** (`X-Request-ID`, sinh nếu thiếu) đưa vào mọi dòng log.
- **CSRF nhẹ:** với POST/PUT/PATCH/DELETE, thiếu `X-Requested-With: fetch` → `403 FORBIDDEN`.
- **Security headers:** `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; worker-src 'self' blob:; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Strict-Transport-Security` (khi có TLS).
- **Log có cấu trúc** (JSON): chỉ ID, route, status, độ trễ; lọc bỏ body.

### 3.4 Lược đồ CSDL (Alembic, migration đầu tiên)
Bật extension `pgvector` và `citext`.

| Bảng | Cột chính |
|---|---|
| `users` | `id uuid pk`, `email citext unique`, `display_name`, `password_hash`, `role enum(member,lead,admin)`, `status enum(active,disabled)`, `must_change_password bool`, `failed_attempts int`, `locked_until timestamptz null`, `last_login_at`, `created_at` |
| `sessions` | `id uuid pk`, `token_hash char(64) unique`, `user_id fk`, `created_at`, `last_seen_at` |
| `documents` | `id uuid pk`, `name text`, `size_bytes bigint`, `sha256 char(64)`, `storage_key text`, `page_count int null`, `status enum(processing,ready,failed)`, `error_code text null`, `unreadable_pages int[] default '{}'`, `uploaded_by fk`, `uploaded_at`, `processed_at null`, `deleted_at null` |
| `document_pages` | `document_id fk`, `page int`, `text text`, `pk(document_id, page)` |
| `chunks` | `id uuid pk`, `document_id fk`, `page int`, `char_start int`, `char_end int`, `text text`, `embedding vector(EMBEDDING_DIM)`; chỉ mục HNSW `vector_cosine_ops`; chỉ mục `(document_id)` |
| `conversations` | `id uuid pk`, `user_id fk`, `title text`, `created_at`, `last_activity_at` |
| `messages` | `id uuid pk`, `conversation_id fk`, `question text`, `answer text`, `lang text`, `answer_type enum(answered,partial,not_found) null`, `status enum(streaming,completed,stopped,failed)`, `missing text null`, `error_code text null`, `created_at`, `completed_at null`, `ttft_ms int null`, `total_ms int null`, `llm_input_tokens int null`, `llm_output_tokens int null` |
| `citations` | `message_id fk`, `idx int`, `document_id fk`, `page int`, `chunk_id fk`, `pk(message_id, idx)` |
| `audit_logs` | `id bigserial`, `ts`, `actor_user_id null`, `action text`, `target text null`, `result enum(success,failure)`; **chỉ INSERT** (thu hồi quyền UPDATE/DELETE của role ứng dụng) |
| `events` | `id bigserial`, `ts`, `name text`, `user_id null`, `session_id null`, `props jsonb` |

Ràng buộc: khóa ngoại có `ON DELETE` rõ ràng (`document_pages`, `chunks` xóa theo `documents`; `citations` giữ `document_id` kể cả khi tài liệu bị xóa mềm). `embedding` kích thước lấy từ `EMBEDDING_DIM` lúc chạy migration.

### 3.5 Port và bản giả
| Port | Giao diện | Bản giả (test) |
|---|---|---|
| `StoragePort` | `put(key, stream) -> None`, `open(key) -> stream`, `size(key)`, `delete(key)` | lưu thư mục tạm |
| `EmbeddingPort` | `embed(texts: list[str]) -> list[list[float]]` | `FakeEmbedding`: vector xác định theo băm từ khóa (cùng từ → gần nhau) |
| `LLMPort` | `stream(messages) -> AsyncIterator[str]`, trả thêm `usage(input_tokens, output_tokens)`; ném `LLMTimeout`, `LLMRateLimited`, `LLMError` | `FakeLLM` (kịch bản — xem T13) |
Adapter thật chọn theo `EMBEDDING_PROVIDER`, `LLM_PROVIDER`; adapter thật cụ thể do T08/T13 hiện thực.

Lưu trữ file: thư mục `STORAGE_DIR`, mã hóa khi lưu bằng **mã hóa ở mức đĩa/volume hoặc dịch vụ lưu trữ** (AC-060.1) — không mã hóa ở tầng ứng dụng để còn hỗ trợ `Range` (T17). Ghi rõ điều này trong README của `api/`.

### 3.6 CLI
`python -m app.cli create-user --email --name --role --password` (băm argon2 qua `core/security.py`; đặt `must_change_password=true` nếu có `--temp`) · `disable-user --email`.

## 4. Tệp sẽ tạo
Toàn bộ cây ở 3.1, migration `0001_init.py`, `tests/conftest.py` (DB test, client, fixtures).

## 5. Tiêu chí hoàn thành
- [ ] `alembic upgrade head` tạo đủ bảng trên Postgres sạch; `alembic downgrade base` chạy được.
- [ ] Mọi lỗi (404, 422, 500, `AppError`) trả đúng định dạng chuẩn — có test.
- [ ] Yêu cầu POST thiếu `X-Requested-With` → 403; GET không bị ảnh hưởng.
- [ ] Header bảo mật có mặt trong mọi phản hồi (test).
- [ ] Log không chứa body yêu cầu/phản hồi (test với body chứa chuỗi đánh dấu).
- [ ] `audit_logs` không UPDATE/DELETE được bằng role ứng dụng (test).
- [ ] Tạo được người dùng bằng CLI; mật khẩu lưu băm argon2.
- [ ] Các port có bản giả và test hợp đồng cho từng port.

## 6. Kiểm thử
pytest với Postgres thật (container); test migration lên/xuống; test middleware và xử lý lỗi.

## 7. Ghi chú
`EMBEDDING_DIM` phải chốt trước khi chạy migration vì đổi sau sẽ cần tạo lại cột và tính lại toàn bộ vector. Chốt cùng việc #1 ở kế hoạch.
