# T04 — Backend đăng nhập và phiên

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / Backend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T03 |
| **Mở khóa** | T07 và mọi API cần đăng nhập |
| **Liên quan** | US-001 (AC-001.1 – 001.6); US-003 (AC-003.1/2); NFR-S01, S03, S04; RSK-009 |

## 1. Mục tiêu
Chỉ người có tài khoản do Admin tạo mới truy cập được; phiên an toàn, hết hạn sau 8 giờ không thao tác; chống dò mật khẩu; người bị vô hiệu hóa mất quyền ngay.

## 2. Phạm vi
**Trong:** đăng nhập/đăng xuất, `GET /me`, đổi mật khẩu, phiên, khóa tạm, bắt buộc đổi mật khẩu tạm, dependency kiểm tra đăng nhập/vai trò, nhật ký đăng nhập.
**Ngoài:** màn hình quản trị người dùng (US-002 – 005 — làm sau; dùng CLI T03 để tạo người dùng).

## 3. Đặc tả chi tiết

### 3.1 Endpoint
| Endpoint | Request | Thành công | Lỗi |
|---|---|---|---|
| `POST /api/auth/login` | `{email, password}` | `200` `CurrentUser` + `Set-Cookie: sid=…` | `401 INVALID_CREDENTIALS` · `423 ACCOUNT_LOCKED` (`details.retry_after_s`) · `403 ACCOUNT_DISABLED` |
| `POST /api/auth/logout` | — | `204`, xóa phiên + cookie | — |
| `GET /api/me` | — | `200` `CurrentUser` | `401 UNAUTHENTICATED` / `SESSION_EXPIRED` |
| `POST /api/auth/change-password` | `{current_password?, new_password}` | `204` | `422 VALIDATION_ERROR` (`message_key=auth.password_policy`) · `401 INVALID_CREDENTIALS` nếu `current_password` sai |

`CurrentUser = {id, email, display_name, role: "member"|"lead"|"admin", must_change_password: bool}`.

### 3.2 Thuật toán đăng nhập
1. Chuẩn hóa email (trim, chữ thường). Không tìm thấy người dùng → băm một mật khẩu giả để cùng thời gian xử lý, rồi `401 INVALID_CREDENTIALS` (không tiết lộ email có tồn tại hay không — AC-001.2).
2. `status=disabled` → `403 ACCOUNT_DISABLED` (chỉ trả khi mật khẩu **đúng**, để không lộ trạng thái tài khoản cho người dò).
3. `locked_until > now` → `423 ACCOUNT_LOCKED` với `retry_after_s`.
4. Sai mật khẩu: `failed_attempts += 1`; đạt **5** lần trong cửa sổ 15 phút → `locked_until = now + 15 phút`, đặt lại bộ đếm. Trả `401 INVALID_CREDENTIALS`.
5. Đúng: `failed_attempts = 0`, cập nhật `last_login_at`, tạo phiên mới, đặt cookie.
6. Mọi kết quả (thành công/thất bại, lý do) ghi `audit_logs` (`action=auth.login`) và sự kiện analytics EV-001/EV-002 (`reason`: `invalid_credentials|locked|disabled`).

### 3.3 Phiên
- Token ngẫu nhiên 256 bit (`secrets.token_urlsafe(32)`); chỉ lưu `sha256(token)` trong `sessions.token_hash`.
- Cookie `sid`: `HttpOnly; Secure; SameSite=Strict; Path=/`; (dev cục bộ không HTTPS → cho phép tắt `Secure` bằng cấu hình).
- **Hết hạn khi rảnh:** `now − last_seen_at > SESSION_IDLE_HOURS (8h)` → `401 SESSION_EXPIRED`. Mỗi yêu cầu hợp lệ cập nhật `last_seen_at` (giới hạn ghi tối đa 1 lần/phút để giảm tải).
- Đăng nhập mới không hủy các phiên khác; đổi mật khẩu **hủy mọi phiên khác** của người đó.
- Mỗi yêu cầu kiểm tra lại `users.status`: bị vô hiệu hóa → xóa phiên, trả `401 SESSION_EXPIRED` (đảm bảo ≤ 60 giây theo AC-003.1; thực tế là tức thì ở yêu cầu kế tiếp).

### 3.4 Dependency cho các router
- `current_user` → trả `User` hoặc ném `UNAUTHENTICATED`/`SESSION_EXPIRED`.
- `require_role(*roles)` → `403 FORBIDDEN`.
- Khi `must_change_password=true`: mọi endpoint **trừ** `/me`, `/auth/logout`, `/auth/change-password` trả `403 PASSWORD_CHANGE_REQUIRED` (AC-001.4).
- Hàm `revoke_user_sessions(user_id)` dùng cho vô hiệu hóa; sau khi T09 xong sẽ phát `session.revoked`.

### 3.5 Chính sách mật khẩu
≥ 10 ký tự, có ít nhất một chữ cái và một chữ số; mật khẩu mới ≠ mật khẩu hiện tại. Băm **argon2id** (tham số mặc định của `argon2-cffi`), `needs_rehash` được kiểm tra khi đăng nhập. Không bao giờ ghi mật khẩu vào log.

## 4. Tệp sẽ tạo
`routers/auth.py`, `services/auth.py`, `core/security.py` (băm, token), `deps.py` (`current_user`, `require_role`), `tests/test_auth_*.py`.

## 5. Tiêu chí hoàn thành
- [ ] AC-001.1: đăng nhập đúng → `200` + cookie; `GET /me` trả đúng người.
- [ ] AC-001.2: sai email hoặc sai mật khẩu cho **cùng** phản hồi (cùng mã, cùng thông điệp, thời gian chênh lệch không đáng kể).
- [ ] AC-001.3: 5 lần sai liên tiếp trong 15 phút → lần thứ 6 nhận `423` với `retry_after_s`; sau 15 phút đăng nhập lại được.
- [ ] AC-001.4: tài khoản `must_change_password` bị chặn mọi API khác bằng `PASSWORD_CHANGE_REQUIRED` cho tới khi đổi mật khẩu đạt chính sách.
- [ ] AC-001.5: phiên không thao tác 8 giờ → `401 SESSION_EXPIRED` (test bằng đồng hồ giả).
- [ ] AC-001.6: gọi bất kỳ endpoint bảo vệ nào không có cookie → `401`, không trả dữ liệu.
- [ ] AC-003.1/003.2: vô hiệu hóa người dùng → phiên đang có mất hiệu lực ở yêu cầu kế tiếp; đăng nhập đúng mật khẩu nhận `ACCOUNT_DISABLED`.
- [ ] Cookie có đủ cờ `HttpOnly`, `Secure`, `SameSite=Strict` (test header).
- [ ] Mật khẩu lưu argon2id; token phiên lưu băm (kiểm tra bằng truy vấn DB).
- [ ] Nhật ký đăng nhập ghi đúng thời điểm, người, kết quả; không chứa mật khẩu.

## 6. Kiểm thử
pytest cho từng AC trên; kiểm thử đồng thời (nhiều lần đăng nhập sai song song không vượt bộ đếm sai); đồng hồ giả (freezegun) cho hết hạn và khóa.

## 7. Ghi chú
Không có "quên mật khẩu qua email" trong MVP (D-007); Admin đặt lại (US-004, làm sau). Cần kênh an toàn để giao mật khẩu tạm (ASM-103).
