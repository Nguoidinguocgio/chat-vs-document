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

### 3.1 Cấu trúc `api/src/main/java/com/chatvsdoc/api/`
```
ChatVsDocApplication.java   @SpringBootApplication
config/            AppProperties (@ConfigurationProperties), SecurityConfig, WebConfig, JacksonConfig
common/            AppException, ErrorCode, GlobalExceptionHandler, RequestIdFilter, CsrfHeaderFilter, SecurityHeadersFilter
domain/            entity/ (JPA) + repository/ (Spring Data JPA; truy vấn vector dùng JdbcTemplate/native query)
web/               AuthController, DocumentController, EventsController, ChatController, CitationController, AnalyticsController
service/           auth, documents, pdf pipeline, retrieval, answering, events, audit, analytics
port/              StoragePort, EmbeddingPort, LlmPort  (+ port/adapter/ và test: port/fake/)
cli/               CliRunner (profile `cli`): create-user, disable-user, search (T12)
src/main/resources/ application.yml, db/migration/V1__init.sql (Flyway)
```

### 3.2 Lỗi chuẩn
`AppException(code, status, messageKey, details)`; một `@RestControllerAdvice` (`GlobalExceptionHandler`) chuyển thành `{"error":{"code","message_key","details"}}`. Lỗi không lường trước → `500 INTERNAL_ERROR` với `message_key="errors.generic"`, ghi log đầy đủ (không kèm nội dung nghiệp vụ). Lỗi kiểm tra đầu vào của Bean Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`, JSON sai định dạng) → `422 VALIDATION_ERROR` cùng định dạng. Mọi lỗi của Spring Security (401/403) và 404 mặc định cũng được ánh xạ về định dạng này (`AuthenticationEntryPoint`, `AccessDeniedHandler`).

### 3.3 Middleware (Servlet `Filter` / `OncePerRequestFilter`)
- **Request ID** (`X-Request-ID`, sinh nếu thiếu) đưa vào mọi dòng log qua MDC.
- **CSRF nhẹ:** với POST/PUT/PATCH/DELETE, thiếu `X-Requested-With: fetch` → `403 FORBIDDEN` (tắt CSRF mặc định của Spring Security, dùng filter này kèm cookie `SameSite=Lax`).
- **Security headers:** `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; worker-src 'self' blob:; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Strict-Transport-Security` (khi có TLS).
- **Log có cấu trúc** (JSON, Logback + `logstash-logback-encoder`): chỉ ID, route, status, độ trễ; lọc bỏ body.

### 3.4 Lược đồ CSDL (Flyway, migration đầu tiên `V1__init.sql`)
Bật extension `vector` (pgvector) và `citext`. Kiểu enum dùng `CREATE TYPE` của PostgreSQL, ánh xạ trong JPA bằng `@Enumerated(EnumType.STRING)` + `@JdbcType(PostgreSQLEnumJdbcType.class)`; cột `vector` ánh xạ qua `pgvector-java` (`PGvector`).

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

Ràng buộc: khóa ngoại có `ON DELETE` rõ ràng (`document_pages`, `chunks` xóa theo `documents`; `citations` giữ `document_id` kể cả khi tài liệu bị xóa mềm). `embedding` kích thước lấy từ `EMBEDDING_DIM` lúc chạy migration (placeholder Flyway `${embeddingDim}`).

### 3.5 Port và bản giả
| Port | Giao diện | Bản giả (test) |
|---|---|---|
| `StoragePort` | `void put(String key, InputStream in)`, `InputStream open(String key)`, `long size(String key)`, `void delete(String key)` | lưu thư mục tạm |
| `EmbeddingPort` | `List<float[]> embed(List<String> texts)` | `FakeEmbedding`: vector xác định theo băm từ khóa (cùng từ → gần nhau) |
| `LlmPort` | `void stream(List<LlmMessage> messages, Consumer<String> onDelta)` trả `LlmUsage(inputTokens, outputTokens)`; ném `LlmTimeoutException`, `LlmRateLimitedException`, `LlmException` (hủy bằng `CancellationToken`/thread interrupt) | `FakeLlm` (kịch bản — xem T13) |
Adapter thật chọn theo `EMBEDDING_PROVIDER`, `LLM_PROVIDER`; adapter thật cụ thể do T08/T13 hiện thực.

Lưu trữ file: thư mục `STORAGE_DIR`, mã hóa khi lưu bằng **mã hóa ở mức đĩa/volume hoặc dịch vụ lưu trữ** (AC-060.1) — không mã hóa ở tầng ứng dụng để còn hỗ trợ `Range` (T17). Ghi rõ điều này trong README của `api/`.

### 3.6 CLI
`java -jar api.jar --spring.profiles.active=cli create-user --email … --name … --role … --password …` (hoặc `./mvnw spring-boot:run -Dspring-boot.run.arguments="create-user …"`); băm **Argon2id** qua `Argon2PasswordEncoder` (Spring Security) bọc trong `PasswordHasher`; đặt `must_change_password=true` nếu có `--temp` · `disable-user --email`. Script tiện dụng: `ops/scripts/create-user.ps1`.

## 4. Tệp sẽ tạo
Toàn bộ cây ở 3.1, migration `V1__init.sql`, `src/test/java/.../support/` (cấu hình Testcontainers dùng chung, `MockMvc`/client, fixtures).

## 5. Tiêu chí hoàn thành
- [ ] Flyway `migrate` tạo đủ bảng trên Postgres sạch; kịch bản `clean` + `migrate` lại chạy được (không dùng downgrade; mỗi migration về sau có script hoàn tác ghi chú nếu cần).
- [ ] Mọi lỗi (404, 422, 500, `AppError`) trả đúng định dạng chuẩn — có test.
- [ ] Yêu cầu POST thiếu `X-Requested-With` → 403; GET không bị ảnh hưởng.
- [ ] Header bảo mật có mặt trong mọi phản hồi (test).
- [ ] Log không chứa body yêu cầu/phản hồi (test với body chứa chuỗi đánh dấu).
- [ ] `audit_logs` không UPDATE/DELETE được bằng role ứng dụng (test).
- [ ] Tạo được người dùng bằng CLI; mật khẩu lưu băm argon2.
- [ ] Các port có bản giả và test hợp đồng cho từng port.

## 6. Kiểm thử
JUnit 5 + Spring Boot Test với Postgres thật (Testcontainers, image `pgvector/pgvector:pg16`); test migration từ DB trống; test filter và xử lý lỗi bằng `MockMvc`.

## 7. Ghi chú
`EMBEDDING_DIM` phải chốt trước khi chạy migration vì đổi sau sẽ cần tạo lại cột và tính lại toàn bộ vector. Chốt cùng việc #1 ở kế hoạch.

**Ghi chú khi hiện thực (2026-10-26):**
- `audit_logs` chỉ-INSERT bằng **trigger** (chặn UPDATE/DELETE/TRUNCATE, có hiệu lực cả với chủ bảng/superuser) cộng `REVOKE`; vì vậy `audit_logs.actor_user_id` không có khóa ngoại (`ON DELETE SET NULL` là một UPDATE).
- `citations.chunk_id` `ON DELETE SET NULL`; `citations.document_id` `ON DELETE RESTRICT` (xóa mềm không ảnh hưởng, xóa cứng tài liệu còn được trích dẫn bị chặn).
- Cột `chunks.embedding` không ánh xạ JPA; đọc/ghi qua `ChunkVectorRepository` (JdbcTemplate + `PGvector`).
- `citext` chỉ không phân biệt hoa/thường khi tham số cũng là `citext`: `UserRepository.findByEmail` ép kiểu tường minh.
- Bản giả `FakeEmbedding`/`FakeLlm` đặt ở mã chính (`port/fake/`) để chọn được bằng `*_PROVIDER=fake` khi chạy E2E, không chỉ trong test. Bean embedding/LLM là `@Lazy` để ứng dụng khởi động được trước khi có adapter thật (T08/T13).
- CLI: thiếu `--name`/`--password` thì đọc `CLI_NAME`/`CLI_PASSWORD` — trên Windows JVM làm hỏng tiếng Việt trong đối số dòng lệnh; `create-user.ps1` dùng cách này và chạy `java -jar` trực tiếp.
- Thêm mã lỗi chung `METHOD_NOT_ALLOWED` 405, `PAYLOAD_TOO_LARGE` 413, `UNSUPPORTED_MEDIA_TYPE` 415 (cần bổ sung vào hợp đồng ở T06).
