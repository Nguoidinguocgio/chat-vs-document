# T06 — Hợp đồng API (OpenAPI) và API giả (MSW)

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / FE + BE |
| **Ước tính** | 2 ngày |
| **Phụ thuộc** | T01 |
| **Mở khóa** | T05, T10, T11, T15, và mọi tích hợp FE–BE |
| **Liên quan** | SDD 3.5.4 (IF-01…IF-07), giao thức SSE |

## 1. Mục tiêu
Chốt **một** hợp đồng API duy nhất mà FE và BE cùng tuân theo; FE phát triển độc lập bằng API giả; BE bị kiểm tra bằng chính hợp đồng. Đây là task nhỏ nhưng quyết định việc tích hợp muộn hay sớm.

## 2. Phạm vi
**Trong:** `contracts/openapi.yaml` cho các endpoint ở mục 3; sinh kiểu TypeScript; MSW (kể cả giả lập SSE); kiểm tra BE khớp hợp đồng.
**Ngoài:** endpoint quản trị, báo cáo, xóa tài liệu, tóm tắt (làm sau).

## 3. Đặc tả chi tiết

### 3.1 Schema dùng chung
```
Error        {error:{code:string, message_key:string, details:object}}
CurrentUser  {id, email, display_name, role:"member"|"lead"|"admin", must_change_password:bool}
Document     {id, name, page_count:int|null, status:"processing"|"ready"|"failed",
              error_code:null|"TOO_MANY_PAGES"|"PASSWORD_PROTECTED"|"SCANNED_PDF"|"CORRUPTED"|"SYSTEM_ERROR",
              error_details:{page_count?:int}|null, unreadable_pages:int[],
              uploaded_by:{id, display_name}, uploaded_at:ISO, can_delete:bool}
DocumentList {items:Document[], count:int, limit:20}
Conversation {id, title, created_at, last_activity_at}
Message      {id, question, answer, lang:"vi"|"en", status:"streaming"|"completed"|"stopped"|"failed",
              answer_type:null|"answered"|"partial"|"not_found", missing:string|null,
              error_code:null|"timeout"|"llm_error"|"rate_limited", citations:Citation[], created_at}
Citation     {index:int, document_id, document_name, page:int, document_deleted:bool}
CitationDetail {document_id, document_name, page, page_count,
                highlight:{snippet:string, page_text_start:int, page_text_end:int}|null}
```
`error_details.page_count` có mặt khi `error_code=TOO_MANY_PAGES` (để hiển thị "File có {n} trang, vượt giới hạn 100 trang.").

### 3.2 Endpoint (đường dẫn và mã lỗi)
| Nhóm | Endpoint | Thành công | Lỗi riêng |
|---|---|---|---|
| Auth (T04) | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me`, `POST /api/auth/change-password` | 200/204 | `INVALID_CREDENTIALS` 401, `ACCOUNT_LOCKED` 423, `ACCOUNT_DISABLED` 403 |
| Tài liệu (T07, T09) | `GET /api/documents` → `DocumentList`; `POST /api/documents` (multipart `file`) → 201 `Document`; `POST /api/documents/{id}/retry` → 202 `Document` | | `NOT_PDF` 400, `FILE_TOO_LARGE` 413, `DOCUMENT_LIMIT_REACHED` 409, `NOT_RETRYABLE` 409 |
| Sự kiện (T09) | `GET /api/events` (SSE) | `text/event-stream` | — |
| Analytics (T09) | `POST /api/analytics/events` `{events:[{name, ts, props}]}` | 204 | `VALIDATION_ERROR` 422 |
| Hội thoại (T14) | `POST /api/conversations` → 201; `GET /api/conversations` → `Conversation[]`; `GET /api/conversations/{id}` → `{conversation, messages:Message[]}` | | `NOT_FOUND` 404 |
| Hỏi đáp (T14) | `POST /api/conversations/{id}/messages` `{question}` → SSE | | `QUESTION_INVALID` 422, `NO_READY_DOCUMENTS` 409, `ANSWER_IN_PROGRESS` 409 |
| Điều khiển (T17) | `POST /api/messages/{id}/stop` → 204; `POST /api/messages/{id}/regenerate` → SSE | | `NOT_REGENERABLE` 409 |
| Nguồn (T17) | `GET /api/citations/{message_id}/{index}` → `CitationDetail`; `GET /api/documents/{id}/file` (hỗ trợ `Range`) | 200/206 | `DOCUMENT_DELETED` 410 |

### 3.3 SSE
Khung theo chuẩn: `event: <tên>\ndata: <JSON một dòng>\n\n`; có thể có dòng bình luận `: ping`.

**`/api/events`** (có `id:` tăng dần): `document.created` `{Document}` · `document.status_changed` `{Document}` · `document.deleted` `{id}` · `session.revoked` `{}` · `resync` `{}` (client phải tải lại danh sách) · `heartbeat` `{}` mỗi 20 s.

**Hỏi đáp**: `meta {message_id, lang}` · `status {phase:"retrieving"}` · `delta {text}` · `citation {index, document_id, document_name, page, document_deleted}` · `done {answer_type, missing?}` · `error {code:"timeout"|"llm_error"|"rate_limited"}`. Luôn kết thúc bằng đúng **một** `done` hoặc `error`.

### 3.4 Công cụ
- `contracts/openapi.yaml` (OpenAPI 3.1); mô tả SSE bằng `text/event-stream` + tiện ích mở rộng `x-sse-events` liệt kê sự kiện.
- `openapi-typescript` → `web/src/shared/api/schema.ts` (script `npm run gen:api`); CI kiểm tra file sinh ra không lệch so với commit.
- **Kiểm tra BE khớp hợp đồng:** test so sánh `app.openapi()` với hợp đồng cho các endpoint đã hiện thực (đường dẫn, phương thức, mã trạng thái, schema); dùng `schemathesis` (hoặc tương đương) chạy với các endpoint không phải SSE.
- **MSW (`web/src/mocks`):** handler cho mọi endpoint ở 3.2; dữ liệu mẫu lấy từ prototype (tài liệu HopDong_A…, câu trả lời có `[1][2]`). Chọn kịch bản bằng cookie/`localStorage` giả hoặc query `?mock=` ở môi trường dev: `answered`, `not_found`, `partial`, `interrupted`, `timeout`, `llm_error`, `empty_workspace`, `processing_only`, `all_failed`, `near_limit`. SSE giả dùng `ReadableStream` phát từng `delta` mỗi ~45 ms. Dùng chung cho Vitest và Playwright (mode mock).

## 4. Tệp sẽ tạo
`contracts/openapi.yaml`, `web/src/shared/api/schema.ts` (sinh), `web/src/mocks/{handlers,data,sse}.ts`, `web/src/mocks/browser.ts` + `server.ts`, `api/tests/test_contract.py`.

## 5. Tiêu chí hoàn thành
- [ ] `openapi.yaml` hợp lệ (CI lint) và mô tả đủ mọi endpoint, schema, mã lỗi ở 3.1–3.2.
- [ ] `npm run gen:api` tạo kiểu TS; build web dùng kiểu này, không có `any` ở lớp API.
- [ ] Test hợp đồng ở BE chạy trong CI (ban đầu đánh dấu "chưa hiện thực" cho endpoint chưa có, rồi bật dần theo task).
- [ ] MSW chạy ở dev server (`npm run dev:mock`) và trong Vitest; mọi kịch bản liệt kê ở 3.4 gọi được.
- [ ] SSE giả phát đúng thứ tự `meta → status → delta… → citation… → done` và các kịch bản lỗi; kết thúc bằng đúng một `done`/`error`.
- [ ] Hợp đồng được cả FE lẫn BE lead duyệt (ghi trong PR).

## 6. Kiểm thử
Test hợp đồng BE (trên); test MSW bằng Vitest (đọc luồng SSE giả bằng cùng bộ phân tích dùng ở T16).

## 7. Ghi chú
Mọi thay đổi hợp đồng về sau đi qua PR sửa `openapi.yaml` trước, rồi mới sửa mã hai phía. Tên endpoint là đề xuất của SDD; nếu BE cần đổi, đổi ở đây một lần.
