# T02 — Design tokens và thư viện UI cơ sở

| | |
|---|---|
| **Giai đoạn / Loại** | 0 — Nền tảng / Frontend |
| **Ước tính** | 3 ngày |
| **Phụ thuộc** | T01 |
| **Mở khóa** | T05 và mọi task FE |
| **Liên quan** | SDD 2.3, 3.4 (tokens), 3.5.2; NFR-U03; prototype [SCR-03](../design/html_design/SCR-03_workspace-chat.html) |

## 1. Mục tiêu
Một thư viện `shared/ui` dùng chung để mọi màn hình nhất quán, tiếp cận được và không phải tự viết lại nút, hộp thoại, toast… Giao diện phải khớp prototype.

## 2. Phạm vi
**Trong:** tokens, font, các component cơ sở, trang trình diễn component, kiểm thử a11y.
**Ngoài:** component nghiệp vụ (AnswerCard, DocumentRow… thuộc task feature); chế độ tối (DD-05).

## 3. Đặc tả chi tiết

### 3.1 Tokens (`src/shared/ui/tokens.css`)
Định nghĩa bằng CSS variables, ánh xạ vào `tailwind.config` (không dùng mã màu cứng ngoài file này; thêm luật lint cấm `#rrggbb` trong `src/**/*.vue|ts|css` trừ `tokens.css`).

| Nhóm | Token (đúng giá trị SDD 3.4) |
|---|---|
| Màu | `--color-bg #FFFFFF`, `--color-bg-subtle #F8FAFC`, `--color-text #0F172A`, `--color-text-muted #475569`, `--color-border #CBD5E1`, `--color-border-strong #64748B`, `--color-primary #1D4ED8`, `--color-primary-hover #1E40AF`, `--color-success #15803D`, `--color-warning #B45309`, `--color-danger #B91C1C`, `--color-highlight #FDE68A`; kèm nền nhạt cho trạng thái: `primary-bg #EFF6FF`, `success-bg #F0FDF4`, `warning-bg #FFFBEB`, `danger-bg #FEF2F2` |
| Chữ | `Inter` (tự host `woff2`, subset `latin` + `latin-ext` + `vietnamese`), dự phòng `"Segoe UI", system-ui`; thang 12/14/16/18/24 px, line-height 16/20/26/28/32 |
| Khoảng cách | thang 4 px: 4, 8, 12, 16, 24, 32, 48 |
| Bo góc | 4 / 8 / 12 px · Đổ bóng: e1 (thẻ), e2 (menu), e3 (hộp thoại/ngăn kéo) |
| Chuyển động | 120 / 200 ms, `cubic-bezier(.2,0,0,1)`; `@media (prefers-reduced-motion: reduce)` đặt về 0 |
| Focus | `outline: 2px solid var(--color-primary); outline-offset: 2px` cho mọi phần tử tương tác (`:focus-visible`) |

### 3.2 Component (`src/shared/ui/components/*`)
Mỗi component: TypeScript props rõ ràng, `forwardRef`, đủ trạng thái hover/focus/active/disabled, test.

| Component | Yêu cầu chính |
|---|---|
| `Button` | variant `primary / secondary / danger / ghost`, size `sm / md`, prop `loading` (spinner, vô hiệu hóa, `aria-busy`); chiều cao ≥ 36 px (≥ 44 px khi `pointer: coarse`) |
| `IconButton` | **bắt buộc** prop `label` → `aria-label`; 32×32 px (44 px khi coarse) |
| `Input`, `Textarea` | nhãn gắn bằng `htmlFor`, prop `error` → `aria-invalid` + `aria-describedby`; viền `border-strong` |
| `Dialog` | Reka UI (Radix Vue); tiêu đề + mô tả, bẫy focus, Esc đóng, trả focus; biến thể xác nhận (nút chính `danger` khi phá hủy) |
| `Menu` / `Popover` | Reka UI; điều hướng mũi tên, `role="menu"` |
| `Tooltip` | hiện khi hover **và** focus; không là cách duy nhất truyền thông tin |
| `Toast` + `ToastRegion` | API `toast.show(text)` / `toast.error(text)`; tối đa 3, thông tin tự ẩn sau 5 s, lỗi giữ đến khi đóng; `aria-live="polite"` (lỗi: `assertive`) |
| `Badge` | `neutral / success / warning / danger / info` |
| `Alert` / `Note` | `info / warning / error`, có icon + chữ; tùy chọn nút hành động bên phải |
| `Skeleton`, `Spinner` | spinner dừng xoay khi reduced-motion; `role="status"` + nhãn ẩn |
| `ProgressBar` | `role="progressbar"`, `aria-valuenow/min/max` |
| `EmptyState` | minh họa (emoji/SVG), tiêu đề, mô tả, hành động |

### 3.3 Trang trình diễn
Route chỉ bật ở môi trường dev: `/dev/ui` liệt kê mọi component ở mọi trạng thái (kể cả lỗi, disabled). Dùng làm đích cho kiểm thử a11y và duyệt thiết kế.

### 3.4 Quy ước
Không import Reka UI trực tiếp ngoài `shared/ui`. Văn bản mặc định của component đến từ props/i18n, không hard-code tiếng Việt trong component dùng chung.

## 4. Tệp sẽ tạo
`shared/ui/tokens.css`, `shared/ui/fonts/*.woff2`, `shared/ui/components/*.vue` (+ `*.test.ts`), `shared/ui/index.ts`, `app/dev/UiShowcase.vue`, cấu hình Tailwind, luật lint cấm màu cứng.

## 5. Tiêu chí hoàn thành
- [ ] Tokens khớp bảng 3.1; có test đọc `tokens.css` kiểm tra các giá trị màu chính xác.
- [ ] Mọi cặp màu chữ/nền dùng trong component đạt ≥ 4.5:1 (thành phần UI ≥ 3:1) — có test tính tỷ lệ tương phản.
- [ ] `/dev/ui` hiển thị đủ component ở mọi trạng thái; axe-core 0 lỗi serious/critical.
- [ ] Mọi component thao tác được bằng bàn phím; vòng focus nhìn thấy được.
- [ ] Chữ tiếng Việt có dấu hiển thị đúng ("Điều khoản thanh toán", "Tải lên") với font Inter đã nhúng.
- [ ] Lint cấm mã màu cứng hoạt động.
- [ ] Giao diện trình diễn khớp prototype (nút, thẻ, thông báo, thanh tiến trình).

## 6. Kiểm thử
Vitest + Vue Test Utils/Testing Library cho hành vi (đóng hộp thoại bằng Esc, toast tự ẩn/giữ lỗi, `loading` vô hiệu hóa nút); axe-core trên `/dev/ui`; test tương phản.

## 7. Ghi chú
Giá trị token là mặc định đề xuất (OQ-UX-03); nếu Design Lead cung cấp nhận diện thương hiệu, chỉ đổi trong `tokens.css`.

**Ghi chú khi hiện thực (2026-10-26):**
- Vue không có `forwardRef`: component dạng ô nhập/nút `defineExpose({ el, focus })` để cha lấy phần tử DOM qua `ref`.
- Văn bản mặc định (Hủy, Xác nhận, Đóng, Đang tải) lấy từ vue-i18n (`src/locales/vi`, khóa `ui.*`); props ghi đè được.
- Font Inter tự host trong `shared/ui/fonts/` chỉ 3 subset latin, latin-ext, vietnamese (giấy phép OFL kèm theo).
- axe-core chạy trên `/dev/ui` trong jsdom (`UiShowcase.test.ts`); jsdom không tính màu nên `color-contrast` được kiểm bằng test tương phản trong `tokens.test.ts`. T21 chạy lại axe trong trình duyệt thật.
- Cấm mã màu cứng: luật ESLint (`Literal`/`TemplateElement` trong script) + test quét toàn bộ `.vue/.ts/.css` (bắt cả `<template>`/`<style>`).
