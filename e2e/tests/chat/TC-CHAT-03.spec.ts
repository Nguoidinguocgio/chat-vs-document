import { expect, test, type Locator } from '@playwright/test'
import { saveVideo } from '../../helpers/report'
import { ask, mockLogin, overall, stepRunner, writeResult } from '../../helpers/steps'

// TC-CHAT-03 — Hai tài liệu mâu thuẫn: nêu cả hai, không tự chọn bên (AC-021.3). Chế độ API giả (MSW), ?mock=answered.
const QUESTION = 'Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?'
// Nguyên văn câu trả lời mẫu ở prototype SDD (docs/design/html_design/SCR-03_workspace-chat.html, answered)
const CONFLICT = 'Đây là mâu thuẫn giữa hai tài liệu nên hệ thống không tự chọn bên nào.'

test('TC-CHAT-03 hai tài liệu mâu thuẫn', async ({ page, browserName }, testInfo) => {
  const { steps, run } = stepRunner(page)
  const card = page.getByTestId('answer-card').last()
  const mark = (l: Locator, on = true) => l.evaluateAll((els, v) => els.forEach((e) => ((e as HTMLElement).style.outline = v)), on ? '3px solid #f59e0b' : '')

  try {
    await run('ready', async () => {
      await mockLogin(page, 'lan@congty.vn', 'answered')
      await expect(page.getByRole('textbox', { name: 'Câu hỏi', exact: true })).toBeEnabled()
      return `Đăng nhập lan@congty.vn → ${page.url()}; ô nhập mở khóa`
    })

    await run('answer', async () => {
      await ask(page, QUESTION)
      await expect(card).toHaveAttribute('data-state', 'answered', { timeout: 15_000 })
      return `Câu trả lời hoàn tất (data-state=answered): "${(await card.innerText()).split('\n')[0]}"`
    })

    await run('both-values', async () => {
      const a = card.locator('li', { hasText: '30 ngày' })
      const b = card.locator('li', { hasText: '45 ngày' })
      await expect(a).toContainText('Hợp đồng A')
      await expect(b).toContainText('Đề xuất B')
      await expect(a.getByRole('button', { name: 'Trích dẫn 1: HopDong_A.pdf, trang 12' })).toBeVisible()
      await expect(b.getByRole('button', { name: 'Trích dẫn 2: DeXuat_B.pdf, trang 4' })).toBeVisible()
      // Mỗi giá trị chỉ gắn trích dẫn của chính nó
      await expect(a.getByRole('button', { name: /^Trích dẫn 2/ })).toHaveCount(0)
      await expect(b.getByRole('button', { name: /^Trích dẫn 1/ })).toHaveCount(0)
      const sources = page.getByTestId('source-list').last()
      await expect(sources.getByRole('button')).toHaveText(['[1] HopDong_A.pdf, trang 12', '[2] DeXuat_B.pdf, trang 4'])
      await mark(a)
      await mark(b)
      return `"${await a.innerText()}" | "${await b.innerText()}" — mỗi dòng có chip trích dẫn riêng; Nguồn: [1] HopDong_A.pdf tr.12, [2] DeXuat_B.pdf tr.4`
    })

    await run('conflict-stated', async () => {
      const para = card.locator('p', { hasText: CONFLICT })
      await expect(para).toHaveCount(1)
      const text = (await para.innerText()).trim()
      expect(text.startsWith(CONFLICT), 'so khớp nguyên văn câu kết luận').toBe(true)
      // Không khẳng định một bên đúng
      const all = await card.innerText()
      for (const bad of [/Hợp đồng A (là )?đúng/i, /Đề xuất B (là )?đúng/i, /nên áp dụng (30|45) ngày/i]) expect(all).not.toMatch(bad)
      await mark(card.locator('li'), false)
      await mark(para)
      return `Câu kết luận: "${text}" — khớp nguyên văn prototype SCR-03; không có câu chọn bên`
    })
  } finally {
    const video = await saveVideo(page)
    writeResult('TC-CHAT-03', {
      status: overall(steps, 4),
      mode: 'mock',
      browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
      steps,
      video: video ?? null,
      defects: [],
    })
  }
  expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
})
