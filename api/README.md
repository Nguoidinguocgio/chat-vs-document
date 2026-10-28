# Backend (`api/`)

Spring Boot 3.3, Java 21, Maven. Gói gốc `com.chatvsdoc.api`.

```
config/   AppProperties (biến môi trường), SecurityConfig, WebConfig (thứ tự filter), JacksonConfig, PortConfig
common/   AppException, ErrorCode, GlobalExceptionHandler, ApiErrorController, các filter:
          RequestIdFilter → SecurityHeadersFilter → AccessLogFilter → CsrfHeaderFilter → Spring Security
domain/   entity/ (JPA) + repository/ (Spring Data; vector qua ChunkVectorRepository + JdbcTemplate)
service/  auth (PasswordHasher Argon2id, UserAdminService), audit
port/     StoragePort, EmbeddingPort, LlmPort · adapter/ (LocalFileStorage) · fake/ (FakeEmbedding, FakeLlm)
cli/      CliRunner (profile cli)
web/      controller REST
```

## Lệnh
```bash
./mvnw spring-boot:run           # chạy (cần PostgreSQL, xem README gốc)
./mvnw verify                    # Spotless + Checkstyle + SpotBugs + toàn bộ test (cần Docker)
./mvnw spotless:apply            # định dạng mã
./mvnw dependency-check:check    # quét lỗ hổng phụ thuộc (chậm; nên có NVD API key)
```

## Quy ước
- **Lỗi**: luôn `{"error":{"code","message_key","details"}}`. Ném `AppException(ErrorCode...)` hoặc `AppException(code, status, messageKey, details)`; lỗi không lường trước → `500 INTERNAL_ERROR` / `errors.generic`. Không đưa nội dung nghiệp vụ vào `details` hay log.
- **JSON**: `snake_case`, thời gian ISO 8601 UTC, trường lạ bị từ chối (422).
- **CSRF**: POST/PUT/PATCH/DELETE phải có `X-Requested-With: fetch`, thiếu → 403.
- **Log**: JSON (logstash-logback-encoder), mỗi dòng có `request_id`. Log truy cập chỉ gồm method, route (không query string), status, `duration_ms`. Không bao giờ log body.
- **CSDL**: thay đổi lược đồ bằng migration Flyway mới `V<n>__mo_ta.sql` (không sửa migration đã chạy). Không có downgrade; ghi chú cách hoàn tác trong đầu file nếu cần. `EMBEDDING_DIM` cố định từ V1 (`vector(1024)` cho bge-m3); đổi số chiều phải tạo lại cột và tính lại toàn bộ vector.
- **audit_logs** chỉ thêm: trigger trong CSDL chặn UPDATE/DELETE/TRUNCATE (có hiệu lực cả với chủ bảng) và quyền UPDATE/DELETE đã bị thu hồi.
- Enum PostgreSQL ánh xạ `@Enumerated(STRING)` + `@JdbcType(PostgreSQLEnumJdbcType.class)`; tên hằng Java trùng nhãn trong CSDL (chữ thường).
- `citext`: tìm theo email phải ép `CAST(:email AS citext)` (xem `UserRepository`) — bind kiểu varchar sẽ so sánh phân biệt hoa/thường.

## Đăng nhập và phiên (T04)
- Cookie `sid` (HttpOnly; Secure; SameSite=Strict; Path=/), token 256 bit, CSDL chỉ lưu SHA-256. Hết hạn khi rảnh `SESSION_IDLE_HOURS`; `last_seen_at` ghi tối đa 1 lần/phút.
- 5 lần sai trong 15 phút → khóa 15 phút (`423 ACCOUNT_LOCKED`, `details.retry_after_s`). Bộ đếm tăng nguyên tử bằng SQL (`AuthService.registerFailure`).
- Sai email và sai mật khẩu cùng phản hồi và cùng chạy Argon2 (băm mật khẩu giả) để không lộ email tồn tại.
- `must_change_password` → mọi `/api/**` trừ `/api/me`, đăng xuất, đổi mật khẩu trả `403 PASSWORD_CHANGE_REQUIRED` (`PasswordChangeRequiredFilter`).
- Vô hiệu hóa (`UserAdminService.disableUser`) xóa mọi phiên ngay và phát `session.revoked` qua SSE.
- Kiểm tra vai trò: `@PreAuthorize("hasRole('ADMIN')")` (vai trò → `ROLE_MEMBER/LEAD/ADMIN`); principal là `AuthUser` (`@AuthenticationPrincipal`).

## Tải lên và xử lý PDF (T07, T08)
- `POST /api/documents` đọc multipart **theo luồng** (commons-fileupload2; multipart của Spring tắt) và dừng đọc ngay khi vượt `MAX_FILE_MB`.
- Giới hạn 20 tài liệu được giữ nguyên tử bằng `pg_advisory_xact_lock` + đếm + chèn trong một giao dịch (test chạy đua 20 vòng × 5 yêu cầu song song).
- Hàng đợi `InProcessJobQueue` (`PROCESSING_WORKERS`, mặc định 2), luôn chạy ngoài luồng HTTP. Khi khởi động, tài liệu `processing` cũ hơn 5 phút được xếp hàng lại.
- `PdfPipeline`: PDFBox → chuẩn hóa NFC → chia đoạn trong từng trang (`Chunker`: 800/1.000 ký tự, chồng lấn 100) → embedding theo lô 32, thử lại 3 lần (1 s/2 s/4 s) → ghi trang + đoạn + trạng thái **trong một giao dịch**. Phân loại lỗi: `TOO_MANY_PAGES`, `PASSWORD_PROTECTED`, `SCANNED_PDF`, `CORRUPTED`, `SYSTEM_ERROR`; một phần trang scan → `ready` kèm `unreadable_pages`.
- Embedding thật: `OllamaEmbeddingAdapter` (`POST {EMBEDDING_BASE_URL}/api/embed`, bge-m3, 1024 chiều, chuẩn hóa độ dài 1).

## Sự kiện thời gian thực và analytics (T09)
- `GET /api/events` (SSE): `document.created`, `document.status_changed`, `document.deleted`, `session.revoked`, `resync`, `heartbeat` (20 s) và `: ping` (10 s); phát lại theo `Last-Event-ID` từ vòng đệm 500 sự kiện / 10 phút; tối đa 3 kết nối mỗi người.
- **Giả định một instance backend**: `InProcessEventBus` chạy trong tiến trình. Nếu chạy nhiều instance, thay bằng PostgreSQL `LISTEN/NOTIFY` sau giao diện `EventBus` (không đổi nghiệp vụ). Hàng đợi xử lý PDF cũng giả định một instance.
- `AnalyticsSchema` là danh sách trắng duy nhất cho tên/thuộc tính sự kiện (máy chủ và client); `POST /api/analytics/events` chỉ nhận các sự kiện client EV-021/022/025/030/050.

## Hợp đồng API (T06)
`contracts/openapi.yaml` là nguồn sự thật. `ContractTest` kiểm (1) mọi route `/api/**` có trong mã đều có trong hợp đồng (so với `/v3/api-docs`, chỉ bật ở profile test hoặc `API_DOCS_ENABLED=true`) và (2) phản hồi thật của các endpoint đã hiện thực hợp lệ theo schema.

## Lưu trữ file và mã hóa khi lưu (AC-060.1)
File PDF gốc nằm trong thư mục `STORAGE_DIR` qua `StoragePort` (`LocalFileStorage`). Ứng dụng **không** mã hóa ở tầng ứng dụng để còn phục vụ `Range` khi xem PDF (T17). Mã hóa khi lưu do hạ tầng đảm nhiệm:
- Máy chủ Windows: đặt `STORAGE_DIR` trên ổ bật **BitLocker**.
- Linux: volume **LUKS/dm-crypt**, hoặc đĩa cloud có mã hóa mặc định (EBS/Persistent Disk/Azure Disk).
- Nếu chuyển sang dịch vụ lưu trữ đối tượng: bật SSE (ví dụ S3 SSE-KMS) và viết adapter `StoragePort` mới (kế thừa `StoragePortContract` trong test).

Khóa lưu trữ do hệ thống sinh, không lấy từ tên file người dùng; `LocalFileStorage` từ chối khóa thoát khỏi thư mục (`..`, đường dẫn tuyệt đối).

## Port và bản giả
| Port | Adapter thật | Bản giả | Chọn bằng |
|---|---|---|---|
| `StoragePort` | `LocalFileStorage` | `LocalFileStorage` trên thư mục tạm | — |
| `EmbeddingPort` | Ollama bge-m3 (T08) | `FakeEmbedding` — băm từ, cùng từ → gần nhau | `EMBEDDING_PROVIDER` |
| `LlmPort` | Anthropic Claude (T13) | `FakeLlm` — kịch bản đoạn chữ/độ trễ/lỗi | `LLM_PROVIDER` |

Adapter mới phải qua bộ test hợp đồng `src/test/.../port/*Contract.java`.

## CLI
```bash
java -jar target/api.jar --spring.profiles.active=cli create-user --email a@b.vn --name "Tên" --role member|lead|admin --password ... [--temp]
java -jar target/api.jar --spring.profiles.active=cli disable-user --email a@b.vn   # vô hiệu hóa + thu hồi mọi phiên
```
Thiếu `--name`/`--password` thì CLI đọc `CLI_NAME`/`CLI_PASSWORD`. Trên Windows nên dùng cách này (script `ops/scripts/create-user.ps1` đã làm sẵn): JVM giải mã đối số dòng lệnh theo code page ANSI nên làm hỏng chữ tiếng Việt, còn biến môi trường được đọc đúng Unicode; mật khẩu cũng không lộ trong danh sách tiến trình.

## Test
`src/test/java/.../support/`: `@IntegrationTest` (Spring Boot + MockMvc + PostgreSQL `pgvector/pgvector:pg16` qua Testcontainers, dùng chung một container), `Fixtures`, `ErrorTestController` (endpoint gây lỗi chỉ có trong test). Testcontainers được ghim ≥ 1.21.4 trong `pom.xml` vì bản 1.19 đi kèm Boot 3.3.5 không chạy với Docker Engine 29.
