import { expect, test, type Page } from '@playwright/test'
import { saveVideo } from '../../helpers/report'
import { CHAT_URL, LOGIN_URL, MOCK_PASSWORD, composer, mockLogin, overall, stepRunner, writeResult } from '../../helpers/steps'

// TC-AUTH-05 — Hết phiên khi đang soạn: giữ nguyên nội dung nháp (AC-001.5). API giả (MSW), ?mock=answered.
const EMAIL = 'lan@congty.vn'
const DRAFT = 'Điều khoản thanh toán là gì?'
// Nguyên văn SDD 3.5.3 / prototype SCR-01 (expired)
const EXPIRED_NOTICE = 'Phiên làm việc đã hết hạn. Đăng nhập lại để tiếp tục — câu hỏi đang soạn đã được giữ lại.'
const EXPIRED_BODY = { error: { code: 'SESSION_EXPIRED', message_key: 'auth.session_expired', details: {} } }

const storage = (page: Page) =>
  page.evaluate(() => ({
    session: Object.fromEntries(Object.keys(sessionStorage).map((k) => [k, sessionStorage.getItem(k)])),
    local: Object.fromEntries(Object.keys(localStorage).map((k) => [k, localStorage.getItem(k)])),
  }))

test('TC-AUTH-05 hết phiên khi đang soạn giữ nguyên nháp', async ({ page, browserName }, testInfo) => {
  const { steps, run } = stepRunner(page)
  let mechanism = ''
  let storageAfterExpiry: Awaited<ReturnType<typeof storage>> | null = null

  try {
    await run('chat', async () => {
      await mockLogin(page, EMAIL, 'answered')
      await expect(composer(page)).toBeEnabled()
      return `Đăng nhập ${EMAIL} → ${page.url()}; ô nhập mở khóa`
    })

    await run('draft-typed', async () => {
      await composer(page).fill(DRAFT)
      await expect(composer(page)).toHaveValue(DRAFT)
      await page.waitForTimeout(700) // debounce lưu nháp 500 ms
      return `Ô nhập chứa "${DRAFT}" (chưa gửi)`
    })

    await run('session-expired', async () => {
      // Cách test case đề xuất: page.route. API giả chạy trong Service Worker (MSW) nên page.route có thể không thấy yêu cầu
      // → thử trước bằng một yêu cầu thăm dò; không được thì thay window.fetch trong trang (cùng hiệu ứng ở lớp shared/api).
      await page.route('**/api/**', (r) => r.fulfill({ status: 401, json: EXPIRED_BODY }))
      const probe = await page.evaluate(async () => (await fetch('/api/documents')).status)
      if (probe === 401) {
        mechanism = 'page.route("**/api/**") → 401 SESSION_EXPIRED'
      } else {
        await page.unroute('**/api/**')
        await page.evaluate((body) => {
          const w = window as unknown as { __origFetch?: typeof fetch }
          w.__origFetch = window.fetch
          window.fetch = async (input, init) => {
            const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
            if (new URL(url, location.origin).pathname.startsWith('/api/'))
              return new Response(JSON.stringify(body), { status: 401, headers: { 'Content-Type': 'application/json' } })
            return w.__origFetch!(input, init)
          }
        }, EXPIRED_BODY)
        mechanism = `page.route không chặn được (yêu cầu thăm dò trả ${probe} — do MSW Service Worker trả lời trước); dùng thay window.fetch → mọi /api/** trả 401 SESSION_EXPIRED`
      }
      await page.getByTestId('send').click()
      await expect(page).toHaveURL(LOGIN_URL, { timeout: 5000 })
      expect(new URL(page.url()).searchParams.get('reason')).toBe('expired')
      await expect(page.getByText(EXPIRED_NOTICE, { exact: true })).toBeVisible()
      storageAfterExpiry = await storage(page)
      return `Cách giả lập: ${mechanism}. Bấm Gửi → ${page.url()}; thông báo nguyên văn: "${EXPIRED_NOTICE}". sessionStorage lúc này: ${JSON.stringify(storageAfterExpiry.session)}`
    })

    await run('draft-kept', async () => {
      await page.unroute('**/api/**')
      await page.evaluate(() => {
        const w = window as unknown as { __origFetch?: typeof fetch }
        if (w.__origFetch) window.fetch = w.__origFetch
      })
      await page.getByLabel('Email').fill(EMAIL)
      await page.getByLabel('Mật khẩu', { exact: true }).fill(MOCK_PASSWORD)
      await page.getByRole('button', { name: 'Đăng nhập' }).click()
      await expect(page).toHaveURL(CHAT_URL)
      await expect(composer(page)).toBeVisible()
      const st = await storage(page)
      const draftKeys = Object.keys(st.session).filter((k) => k.startsWith('draft:'))
      const tokenInLocal = Object.keys(st.local).filter((k) => /token|sid|session/i.test(k))
      const value = await composer(page).inputValue()
      expect(tokenInLocal, 'không có token/sid trong localStorage').toEqual([])
      expect(value, `Ô nhập sau khi đăng nhập lại (sessionStorage draft keys: ${JSON.stringify(draftKeys)})`).toBe(DRAFT)
      return `Vào lại ${page.url()}; ô nhập = "${value}"; nháp trong sessionStorage (${draftKeys.join(', ')}); localStorage không có token/sid (${JSON.stringify(Object.keys(st.local))})`
    })
  } finally {
    const video = await saveVideo(page)
    writeResult('TC-AUTH-05', {
      status: overall(steps, 4),
      mode: 'mock',
      browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
      session_expiry_simulation: mechanism,
      storage_after_expiry: storageAfterExpiry,
      steps,
      video: video ?? null,
      defects: [],
    })
  }
  expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
})
