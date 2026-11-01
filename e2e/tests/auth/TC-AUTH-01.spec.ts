import { mkdirSync, writeFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { reportDir, saveVideo, shot } from '../../helpers/report'

// TC-AUTH-01 — Đăng nhập đúng email và mật khẩu (AC-001.1). Chế độ API giả (MSW).
const EMAIL = 'lan@congty.vn'
const PASSWORD = 'Matkhau-2026'
const CHAT_URL = /^http:\/\/localhost:5173\/c(\/[^?]*)?(\?.*)?$/ // neo từ đầu: không khớp /login?next=/c
const LOGIN_URL = /^http:\/\/localhost:5173\/login(\?.*)?$/

type Status = 'passed' | 'failed' | 'blocked'
type Step = { n: number; status: Status; screenshot: string; note: string }

test('TC-AUTH-01 đăng nhập đúng email và mật khẩu', async ({ page, browserName }, testInfo) => {
  const steps: Step[] = []
  let loginMs = -1
  let current = 0

  // Mỗi bước: chạy kiểm tra, luôn chụp ảnh, ghi trạng thái. `blockedIf` trả lý do khi bước không kiểm được ở chế độ này.
  const run = async (slug: string, check: () => Promise<string>, blockedIf?: () => Promise<string | undefined>) => {
    current += 1
    let status: Status = 'passed'
    let note = ''
    let error: unknown
    try {
      note = await check()
    } catch (e) {
      const reason = blockedIf ? await blockedIf() : undefined
      if (reason) {
        status = 'blocked'
        note = reason
      } else {
        status = 'failed'
        note = String(e instanceof Error ? e.message : e).split('\n')[0]
        error = e
      }
    }
    steps.push({ n: current, status, screenshot: await shot(page, current, slug), note })
    if (error) throw error
  }

  let video: string | undefined
  try {
    await page.goto('/')
    await run('login-page', async () => {
      await expect(page).toHaveURL(LOGIN_URL)
      await expect(page.getByRole('heading', { name: 'Chat với tài liệu' })).toBeVisible()
      return `Chưa đăng nhập → chuyển tới ${page.url()}`
    })

    await run('form-filled', async () => {
      await page.getByLabel('Email').fill(EMAIL)
      await page.getByLabel('Mật khẩu', { exact: true }).fill(PASSWORD)
      await expect(page.getByLabel('Email')).toHaveValue(EMAIL)
      await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveAttribute('type', 'password')
      return 'Email đã điền; ô mật khẩu có type=password (hiển thị dạng che)'
    })

    await run('chat-opened', async () => {
      const t0 = Date.now()
      await page.getByRole('button', { name: 'Đăng nhập' }).click()
      await expect(page).toHaveURL(CHAT_URL, { timeout: 2000 })
      await expect(page.getByRole('button', { name: /Đang đăng nhập/ })).toHaveCount(0)
      loginMs = Date.now() - t0
      expect(loginMs, 'AC-001.1: ≤ 2 giây').toBeLessThanOrEqual(2000)
      return `Từ lúc bấm tới khi mở trang chat: ${loginMs} ms (ngưỡng ≤ 2000 ms). URL: ${page.url()}`
    })

    await run(
      'after-reload',
      async () => {
        await page.reload()
        // Chờ giao diện ổn định (khung chat HOẶC form đăng nhập) rồi mới kiểm URL — tránh kết luận khi đang chuyển hướng.
        await page
          .getByRole('button', { name: /Hội thoại mới/ })
          .or(page.getByRole('button', { name: 'Đăng nhập' }))
          .first()
          .waitFor()
        await expect(page).toHaveURL(CHAT_URL)
        return `Sau F5 vẫn ở ${page.url()}`
      },
      // API giả giữ "phiên" trong biến module (mocks/handlers.ts: mockState.user) nên F5 đặt lại về chưa đăng nhập;
      // bước này chỉ kiểm được trên backend thật (cookie sid HttpOnly) → Bị chặn, không tính Đạt.
      async () =>
        LOGIN_URL.test(page.url())
          ? `BỊ CHẶN ở chế độ API giả: sau F5 bị đưa về ${page.url()} vì phiên giả nằm trong bộ nhớ JS, không phải lỗi ứng dụng. Cần chạy lại ở chế độ real (cookie sid).`
          : undefined,
    )
  } finally {
    video = await saveVideo(page)
    const failed = steps.some((s) => s.status === 'failed') || steps.length < 4
    const blocked = steps.some((s) => s.status === 'blocked')
    mkdirSync(reportDir(), { recursive: true })
    writeFileSync(
      `${reportDir()}/result.json`,
      JSON.stringify(
        {
          test_case: 'TC-AUTH-01',
          status: failed ? 'failed' : blocked ? 'blocked' : 'passed',
          run_at: new Date().toISOString(),
          runner: 'Claude Code (Playwright tự động)',
          mode: 'mock',
          browser: `${browserName} (${testInfo.project.name})`,
          login_ms: loginMs,
          steps,
          video: video ?? null,
          defects: [],
        },
        null,
        2,
      ),
    )
  }
})
