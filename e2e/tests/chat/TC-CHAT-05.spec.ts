import { expect, test } from '@playwright/test'
import { saveVideo } from '../../helpers/report'
import { ask, mockLogin, overall, stepRunner, writeResult } from '../../helpers/steps'

// TC-CHAT-05 — Trả lời một phần: nêu phần tìm thấy và phần không tìm thấy (AC-023.3). API giả, ?mock=partial.
const QUESTION = 'Doanh thu quý 3 và biên lợi nhuận từng mảng là bao nhiêu?'
const MISSING = 'Biên lợi nhuận theo từng mảng không có trong các tài liệu hiện có.' // nguyên văn test case / prototype SCR-03
const DOC = 'BaoCao_Q3_2026_ban_cuoi_cung_da_duyet_v3.pdf'

test('TC-CHAT-05 trả lời một phần', async ({ page, browserName }, testInfo) => {
  const { steps, run } = stepRunner(page)
  const card = page.getByTestId('answer-card').last()
  const outline = (sel: string | null) =>
    page.evaluate((s) => {
      document.querySelectorAll<HTMLElement>('[data-e2e-mark]').forEach((e) => {
        e.style.outline = ''
        e.removeAttribute('data-e2e-mark')
      })
      if (s)
        document.querySelectorAll<HTMLElement>(s).forEach((e) => {
          e.style.outline = '3px solid #f59e0b'
          e.setAttribute('data-e2e-mark', '')
        })
    }, sel)

  try {
    await run('ready', async () => {
      await mockLogin(page, 'lan@congty.vn', 'partial')
      await expect(page.getByRole('textbox', { name: 'Câu hỏi', exact: true })).toBeEnabled()
      return `Đăng nhập lan@congty.vn (?mock=partial) → ${page.url()}; ô nhập mở khóa`
    })

    await run('ask', async () => {
      await ask(page, QUESTION)
      await expect(card).toHaveAttribute('data-state', 'partial', { timeout: 15_000 })
      return 'Câu trả lời hiển thị, data-state=partial'
    })

    await run('found-part', async () => {
      const p = card.locator('p', { hasText: '128,4 tỷ đồng' })
      await expect(p).toContainText('128,4 tỷ đồng, tăng 12% so với cùng kỳ')
      await expect(p.getByRole('button', { name: `Trích dẫn 1: ${DOC}, trang 7` })).toBeVisible()
      await outline('[data-testid="answer-card"] p')
      return `"${(await p.innerText()).trim()}" — có chip [1] (${DOC}, trang 7)`
    })

    await run('missing-part', async () => {
      const box = card.getByTestId('missing')
      await expect(box).toBeVisible()
      const text = (await box.innerText()).replace(/^ⓘ\s*/, '').replace(/\s+/g, ' ').trim()
      expect(text).toBe(`Không tìm thấy: ${MISSING}`)
      await outline('[data-testid="missing"]')
      return `Khối "không tìm thấy": "${text}" — khớp nguyên văn`
    })

    await run('sources', async () => {
      const list = card.getByTestId('source-list')
      await expect(list.getByRole('button')).toHaveText([`[1] ${DOC}, trang 7`])
      await expect(card.getByRole('button', { name: /^Trích dẫn [2-9]/ })).toHaveCount(0)
      await outline('[data-testid="source-list"]')
      return `Danh sách Nguồn chỉ có 1 mục: "[1] ${DOC}, trang 7"; không có nguồn cho phần không tìm thấy`
    })
  } finally {
    const video = await saveVideo(page)
    writeResult('TC-CHAT-05', {
      status: overall(steps, 5),
      mode: 'mock',
      browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
      steps,
      video: video ?? null,
      defects: [],
    })
  }
  expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
})
