# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth\TC-AUTH-05.spec.ts >> TC-AUTH-05 hết phiên khi đang soạn giữ nguyên nháp
- Location: tests\auth\TC-AUTH-05.spec.ts:18:5

# Error details

```
Error: [
 {
  "n": 1,
  "status": "passed",
  "screenshot": "screenshots/01-chat.png",
  "note": "Đăng nhập lan@congty.vn → http://localhost:5173/c?mock=answered; ô nhập mở khóa"
 },
 {
  "n": 2,
  "status": "passed",
  "screenshot": "screenshots/02-draft-typed.png",
  "note": "Ô nhập chứa \"Điều khoản thanh toán là gì?\" (chưa gửi)"
 },
 {
  "n": 3,
  "status": "passed",
  "screenshot": "screenshots/03-session-expired.png",
  "note": "Cách giả lập: page.route không chặn được (yêu cầu thăm dò trả 200 — do MSW Service Worker trả lời trước); dùng thay window.fetch → mọi /api/** trả 401 SESSION_EXPIRED. Bấm Gửi → http://localhost:5173/login?next=/c?mock=answered&reason=expired; thông báo nguyên văn: \"Phiên làm việc đã hết hạn. Đăng nhập lại để tiếp tục — câu hỏi đang soạn đã được giữ lại.\". sessionStorage lúc này: {\"mock:scenario\":\"answered\"}"
 },
 {
  "n": 4,
  "status": "failed",
  "screenshot": "screenshots/04-draft-kept.png",
  "note": "Ô nhập sau khi đăng nhập lại (sessionStorage draft keys: []) | expect(received).toBe(expected) // Object.is equality | Expected: \"Điều khoản thanh toán là gì?\""
 }
]

expect(received).toEqual(expected) // deep equality

- Expected  - 1
+ Received  + 8

- Array []
+ Array [
+   Object {
+     "n": 4,
+     "note": "Ô nhập sau khi đăng nhập lại (sessionStorage draft keys: []) | expect(received).toBe(expected) // Object.is equality | Expected: \"Điều khoản thanh toán là gì?\"",
+     "screenshot": "screenshots/04-draft-kept.png",
+     "status": "failed",
+   },
+ ]
```

# Test source

```ts
  1   | import { expect, test, type Page } from '@playwright/test'
  2   | import { saveVideo } from '../../helpers/report'
  3   | import { CHAT_URL, LOGIN_URL, MOCK_PASSWORD, composer, mockLogin, overall, stepRunner, writeResult } from '../../helpers/steps'
  4   | 
  5   | // TC-AUTH-05 — Hết phiên khi đang soạn: giữ nguyên nội dung nháp (AC-001.5). API giả (MSW), ?mock=answered.
  6   | const EMAIL = 'lan@congty.vn'
  7   | const DRAFT = 'Điều khoản thanh toán là gì?'
  8   | // Nguyên văn SDD 3.5.3 / prototype SCR-01 (expired)
  9   | const EXPIRED_NOTICE = 'Phiên làm việc đã hết hạn. Đăng nhập lại để tiếp tục — câu hỏi đang soạn đã được giữ lại.'
  10  | const EXPIRED_BODY = { error: { code: 'SESSION_EXPIRED', message_key: 'auth.session_expired', details: {} } }
  11  | 
  12  | const storage = (page: Page) =>
  13  |   page.evaluate(() => ({
  14  |     session: Object.fromEntries(Object.keys(sessionStorage).map((k) => [k, sessionStorage.getItem(k)])),
  15  |     local: Object.fromEntries(Object.keys(localStorage).map((k) => [k, localStorage.getItem(k)])),
  16  |   }))
  17  | 
  18  | test('TC-AUTH-05 hết phiên khi đang soạn giữ nguyên nháp', async ({ page, browserName }, testInfo) => {
  19  |   const { steps, run } = stepRunner(page)
  20  |   let mechanism = ''
  21  |   let storageAfterExpiry: Awaited<ReturnType<typeof storage>> | null = null
  22  | 
  23  |   try {
  24  |     await run('chat', async () => {
  25  |       await mockLogin(page, EMAIL, 'answered')
  26  |       await expect(composer(page)).toBeEnabled()
  27  |       return `Đăng nhập ${EMAIL} → ${page.url()}; ô nhập mở khóa`
  28  |     })
  29  | 
  30  |     await run('draft-typed', async () => {
  31  |       await composer(page).fill(DRAFT)
  32  |       await expect(composer(page)).toHaveValue(DRAFT)
  33  |       await page.waitForTimeout(700) // debounce lưu nháp 500 ms
  34  |       return `Ô nhập chứa "${DRAFT}" (chưa gửi)`
  35  |     })
  36  | 
  37  |     await run('session-expired', async () => {
  38  |       // Cách test case đề xuất: page.route. API giả chạy trong Service Worker (MSW) nên page.route có thể không thấy yêu cầu
  39  |       // → thử trước bằng một yêu cầu thăm dò; không được thì thay window.fetch trong trang (cùng hiệu ứng ở lớp shared/api).
  40  |       await page.route('**/api/**', (r) => r.fulfill({ status: 401, json: EXPIRED_BODY }))
  41  |       const probe = await page.evaluate(async () => (await fetch('/api/documents')).status)
  42  |       if (probe === 401) {
  43  |         mechanism = 'page.route("**/api/**") → 401 SESSION_EXPIRED'
  44  |       } else {
  45  |         await page.unroute('**/api/**')
  46  |         await page.evaluate((body) => {
  47  |           const w = window as unknown as { __origFetch?: typeof fetch }
  48  |           w.__origFetch = window.fetch
  49  |           window.fetch = async (input, init) => {
  50  |             const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  51  |             if (new URL(url, location.origin).pathname.startsWith('/api/'))
  52  |               return new Response(JSON.stringify(body), { status: 401, headers: { 'Content-Type': 'application/json' } })
  53  |             return w.__origFetch!(input, init)
  54  |           }
  55  |         }, EXPIRED_BODY)
  56  |         mechanism = `page.route không chặn được (yêu cầu thăm dò trả ${probe} — do MSW Service Worker trả lời trước); dùng thay window.fetch → mọi /api/** trả 401 SESSION_EXPIRED`
  57  |       }
  58  |       await page.getByTestId('send').click()
  59  |       await expect(page).toHaveURL(LOGIN_URL, { timeout: 5000 })
  60  |       expect(new URL(page.url()).searchParams.get('reason')).toBe('expired')
  61  |       await expect(page.getByText(EXPIRED_NOTICE, { exact: true })).toBeVisible()
  62  |       storageAfterExpiry = await storage(page)
  63  |       return `Cách giả lập: ${mechanism}. Bấm Gửi → ${page.url()}; thông báo nguyên văn: "${EXPIRED_NOTICE}". sessionStorage lúc này: ${JSON.stringify(storageAfterExpiry.session)}`
  64  |     })
  65  | 
  66  |     await run('draft-kept', async () => {
  67  |       await page.unroute('**/api/**')
  68  |       await page.evaluate(() => {
  69  |         const w = window as unknown as { __origFetch?: typeof fetch }
  70  |         if (w.__origFetch) window.fetch = w.__origFetch
  71  |       })
  72  |       await page.getByLabel('Email').fill(EMAIL)
  73  |       await page.getByLabel('Mật khẩu', { exact: true }).fill(MOCK_PASSWORD)
  74  |       await page.getByRole('button', { name: 'Đăng nhập' }).click()
  75  |       await expect(page).toHaveURL(CHAT_URL)
  76  |       await expect(composer(page)).toBeVisible()
  77  |       const st = await storage(page)
  78  |       const draftKeys = Object.keys(st.session).filter((k) => k.startsWith('draft:'))
  79  |       const tokenInLocal = Object.keys(st.local).filter((k) => /token|sid|session/i.test(k))
  80  |       const value = await composer(page).inputValue()
  81  |       expect(tokenInLocal, 'không có token/sid trong localStorage').toEqual([])
  82  |       expect(value, `Ô nhập sau khi đăng nhập lại (sessionStorage draft keys: ${JSON.stringify(draftKeys)})`).toBe(DRAFT)
  83  |       return `Vào lại ${page.url()}; ô nhập = "${value}"; nháp trong sessionStorage (${draftKeys.join(', ')}); localStorage không có token/sid (${JSON.stringify(Object.keys(st.local))})`
  84  |     })
  85  |   } finally {
  86  |     const video = await saveVideo(page)
  87  |     writeResult('TC-AUTH-05', {
  88  |       status: overall(steps, 4),
  89  |       mode: 'mock',
  90  |       browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
  91  |       session_expiry_simulation: mechanism,
  92  |       storage_after_expiry: storageAfterExpiry,
  93  |       steps,
  94  |       video: video ?? null,
  95  |       defects: [],
  96  |     })
  97  |   }
> 98  |   expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
      |                                                                                      ^ Error: [
  99  | })
  100 | 
```