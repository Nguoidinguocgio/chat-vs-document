# T05 — Frontend: khung ứng dụng, đăng nhập, API client

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / Frontend |
| **Ước tính** | 4 ngày |
| **Phụ thuộc** | T01, T02, T06 (hợp đồng + MSW) |
| **Mở khóa** | T10, T11, T15 |
| **Liên quan** | SDD 3.1, 3.2, 3.5.2, 3.6 (hết phiên), 3.4 (nháp); US-001; AC-001.1 – 001.6; prototype SCR-01, SCR-02, SCR-06 |

## 1. Mục tiêu
Người dùng đăng nhập được, bị đưa đúng chỗ khi hết phiên hoặc phải đổi mật khẩu; toàn bộ ứng dụng có một cách duy nhất để gọi API, xử lý lỗi, ghi analytics và giữ bản nháp.

## 2. Phạm vi
**Trong:** `app/` (router, guard, bố cục 3 cột rỗng, Error Boundary), `shared/api`, `shared/lib/{draft,analytics,format}`, i18n, `features/auth` (đăng nhập, đổi mật khẩu), trang lỗi.
**Ngoài:** nội dung các cột (T11, T15), kênh SSE (T11), quản trị/báo cáo (làm sau).

## 3. Đặc tả chi tiết

### 3.1 `shared/api`
- `request<T>(method, path, {body, signal})`: `fetch` với `credentials: 'same-origin'`, `Accept: application/json`; POST/PUT/PATCH/DELETE tự thêm `X-Requested-With: fetch`.
- Chuẩn hóa lỗi thành `ApiError {status, code, messageKey, details}` (đọc định dạng lỗi chuẩn; nếu phản hồi không phải JSON → `code: 'NETWORK_OR_UNKNOWN'`, `messageKey: 'errors.generic'`).
- **Xử lý toàn cục:**
  - `401 SESSION_EXPIRED` → xóa toàn bộ cache TanStack Query, chuyển `/login?next=<đường dẫn hiện tại>&reason=expired`; **không** xóa bản nháp.
  - `401 UNAUTHENTICATED` → như trên nhưng không có `reason`.
  - `403 PASSWORD_CHANGE_REQUIRED` → chuyển `/change-password?mode=required`.
  - `403 FORBIDDEN` (từ API) → hiển thị trang 403.
- Kiểu dữ liệu sinh từ OpenAPI (T06): `import type { components } from './schema'`.
- Ghi nhận `X-App-Version` (nếu có) để cảnh báo phiên bản mới (toast "Có phiên bản mới — Tải lại", không tự tải lại).

### 3.2 `app/`
- Router (react-router): `/login`, `/change-password`, `/c`, `/c/:id`, `/403`, `*` (404). Guard theo SDD 3.5.2: chưa đăng nhập → `/login?next=…`; đã đăng nhập vào `/login` → `/c`; `must_change_password` → chỉ vào được `/change-password`.
- `useCurrentUser()` = `GET /api/me` qua TanStack Query (`staleTime: Infinity`, `retry: false`).
- `AppShell`: lưới CSS đúng như prototype (thanh trên 56 px; cột 280 px | chính | 440 px); responsive: ≥ 1280 px 3 cột; 1024–1279 px bảng phải thành ngăn kéo; < 1024 px thanh trái thu thành ngăn kéo với nút ☰. Có liên kết "Bỏ qua tới ô nhập câu hỏi".
- `TopBar` + `AccountMenu`: Đổi mật khẩu, Hướng dẫn sử dụng (liên kết giữ chỗ), Đăng xuất (`POST /auth/logout` rồi xóa cache + nháp). Mục Quản trị/Báo cáo **chưa hiển thị** (chưa có tính năng) nhưng logic ẩn/hiện theo vai trò đã sẵn.
- `ConnectionBanner`: theo `navigator.onLine` + sự kiện `online/offline`; ẩn khi có mạng; nội dung `network.offline`.
- Error Boundary riêng cho từng vùng (thanh trái, chat, bảng phải): lỗi một vùng hiển thị `errors.generic` + nút "Thử lại" trong vùng đó, các vùng khác vẫn dùng được.

### 3.3 `shared/lib`
- `draft`: `saveDraft(userId, conversationId, text)` (debounce 500 ms) / `loadDraft` / `clearDraft` trên `sessionStorage`, khóa `draft:{userId}:{conversationId}`; `clearAllDrafts()` khi đăng xuất hoặc `session.revoked`; **giữ** khi `SESSION_EXPIRED`.
- `analytics`: `track(name, props)` đẩy vào hàng đợi bộ nhớ; gửi lô mỗi 10 s hoặc khi `visibilitychange=hidden` (`navigator.sendBeacon` nếu có, kèm header không thể đặt → dùng `fetch keepalive`). **Danh sách trắng** tên sự kiện và thuộc tính (EV-021, 022, 025, 030, 050…); thuộc tính chỉ được là số, boolean, chuỗi ≤ 64 ký tự thuộc enum — nếu vi phạm thì bỏ sự kiện và `console.warn` (dev). Thuộc tính chung: `event_id`, `timestamp`, `session_id` (UUID sinh mỗi tab), `app_version`.
- `format`: `formatDateTime` (vi-VN, `Asia/Ho_Chi_Minh`, `dd/MM/yyyy HH:mm`), `formatBytes`, `midEllipsis(name, max=34)` (rút gọn giữa).
- `i18n`: i18next, ngôn ngữ `vi`, nạp `locales/vi/*.json`. Tạo đủ khóa của SDD 3.5.3 (`auth.*`, `documents.*`, `chat.*`, `source.*`, `network.*`, `errors.generic`), cộng nhãn giao diện dùng trong T05.

### 3.4 Màn hình
- **SCR-01 Đăng nhập** theo prototype: email, mật khẩu (nút hiện/ẩn), kiểm tra tại chỗ (email rỗng/sai định dạng, mật khẩu rỗng), trạng thái đang gửi, thông báo `auth.invalid_credentials` / `auth.locked` (kèm số phút từ `retry_after_s`) / `auth.disabled`; khi `reason=expired` hiển thị `auth.session_expired`; khi `reason=revoked` (tài khoản bị vô hiệu hóa khi đang dùng) hiển thị `auth.disabled`. Sau khi thành công: chuyển tới `next` (chỉ chấp nhận đường dẫn nội bộ bắt đầu bằng `/` và không phải `//` — chống open redirect) hoặc `/c`.
- **SCR-02 Đổi mật khẩu**: chế độ `required` (không có "mật khẩu hiện tại", không có nút Hủy) và chế độ `change`; checklist chính sách hiển thị trực tiếp (≥ 10 ký tự, có chữ, có số), khớp mật khẩu nhập lại; sau khi lưu chuyển `/c`.
- **SCR-06** Trang 403/404/500 theo prototype.

## 4. Tệp sẽ tạo
`app/{main,router,guards,AppShell,TopBar,AccountMenu,ConnectionBanner,ErrorBoundary}.tsx`, `shared/api/{client,errors,schema.ts}`, `shared/lib/{draft,analytics,format,i18n}/*`, `locales/vi/*.json`, `features/auth/{LoginPage,ChangePasswordPage,api.ts,index.ts}`, `features/auth/*.test.tsx`.

## 5. Tiêu chí hoàn thành
- [ ] AC-001.1/001.2: đăng nhập đúng vào `/c`; sai hiện đúng thông báo, không tiết lộ trường nào sai (dùng MSW).
- [ ] AC-001.3: phản hồi `423` hiển thị `auth.locked` với số phút.
- [ ] AC-001.4: tài khoản `must_change_password` bị giữ ở `/change-password?mode=required` cho tới khi đổi xong.
- [ ] AC-001.5: nhận `SESSION_EXPIRED` khi đang soạn câu hỏi → về `/login`, đăng nhập lại → ô nhập có lại nội dung đã gõ (test với `sessionStorage`).
- [ ] AC-001.6: vào thẳng `/c` khi chưa đăng nhập → `/login`; không có dữ liệu nào được hiển thị.
- [ ] Đăng xuất xóa cache và bản nháp; `next` ngoài trang (`https://x`, `//x`) bị bỏ qua.
- [ ] `fetch` chỉ xuất hiện trong `shared/api` (lint R-3 xanh).
- [ ] `analytics` loại bỏ sự kiện có thuộc tính là chuỗi dài/không thuộc enum (test).
- [ ] Bố cục co giãn đúng 3 mốc (1280 / 1024 / < 1024 px); không cuộn ngang; thao tác bàn phím và focus hợp lý.
- [ ] Lỗi ném trong một vùng không làm sập vùng khác.

## 6. Kiểm thử
Vitest + Testing Library + MSW cho từng AC trên; kiểm thử guard/router; axe-core cho SCR-01/02/06.

## 7. Ghi chú
Tab thứ hai đăng xuất ở tab khác: `401` ở yêu cầu kế tiếp tự đưa về `/login`; có thể bổ sung `BroadcastChannel` sau (không cần ở MVP).
