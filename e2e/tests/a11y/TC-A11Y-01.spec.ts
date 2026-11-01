import { mkdirSync, writeFileSync } from 'node:fs'
import { expect, test, type Page } from '@playwright/test'
import { blocking, hideAxePanel, runAxe, showAxePanel, type AxeState } from '../../helpers/axe'
import { reportDir, saveVideo } from '../../helpers/report'
import { ask, mockLogin, overall, stepRunner, writeResult } from '../../helpers/steps'

// TC-A11Y-01 — axe-core: 0 lỗi serious / critical ở mọi trạng thái chính (NFR-U03, WCAG 2.1 AA). API giả (MSW).
test.setTimeout(240_000)

const PDF = Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n')

test('TC-A11Y-01 axe-core ở mọi trạng thái chính', async ({ page, browserName }, testInfo) => {
  const { steps, run } = stepRunner(page)
  const all: AxeState[] = []
  const extraDir = `${reportDir()}/screenshots/extra`
  mkdirSync(extraDir, { recursive: true })

  // Chạy axe ở một trạng thái, lưu ảnh phụ có bảng tóm tắt; ảnh chính của bước do stepRunner chụp.
  const scan = async (stepStates: AxeState[], name: string, file: string) => {
    const s = await runAxe(page, name)
    all.push(s)
    stepStates.push(s)
    await showAxePanel(page, name, [s])
    await page.screenshot({ path: `${extraDir}/${file}.png` })
    await hideAxePanel(page)
    return s
  }
  const summary = (xs: AxeState[]) =>
    xs.map((s) => `${s.state}: critical ${s.counts.critical}, serious ${s.counts.serious}, moderate ${s.counts.moderate}, minor ${s.counts.minor}`).join(' | ')
  const assertClean = (xs: AxeState[]) => {
    const bad = xs.filter((s) => blocking(s) > 0)
    expect(
      bad.map((s) => `${s.state}: ${s.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => `${v.impact} ${v.id} ×${v.nodes.length}`).join(', ')}`),
      'vi phạm serious/critical',
    ).toEqual([])
  }
  const ready = (p: Page) => expect(p.getByRole('textbox', { name: 'Câu hỏi', exact: true })).toBeVisible()

  try {
    await run('axe-auth-empty', async () => {
      const xs: AxeState[] = []
      await page.goto('/?mock=empty_workspace')
      await expect(page.getByRole('button', { name: 'Đăng nhập' })).toBeVisible()
      await scan(xs, 'Đăng nhập', '01a-login')

      await mockLogin(page, 'moi@congty.vn', 'empty_workspace', /\/change-password/)
      await expect(page.getByRole('heading', { name: 'Đổi mật khẩu' })).toBeVisible()
      await scan(xs, 'Đổi mật khẩu (bắt buộc)', '01b-change-password')

      await mockLogin(page, 'lan@congty.vn', 'empty_workspace')
      await expect(page.getByText('Tải lên tài liệu PDF của dự án để bắt đầu')).toBeVisible()
      await scan(xs, 'Workspace trống', '01c-empty-workspace')
      await showAxePanel(page, 'Bước 1', xs)
      assertClean(xs)
      return summary(xs)
    })
    await hideAxePanel(page)

    await run('axe-upload', async () => {
      const xs: AxeState[] = []
      // Tiếp tục từ workspace trống: chọn file → hàng đợi tải lên / Đang xử lý
      await page.getByTestId('file-input').setInputFiles({ name: 'HopDong_moi.pdf', mimeType: 'application/pdf', buffer: PDF })
      await expect(page.getByText('HopDong_moi.pdf').first()).toBeVisible()
      await scan(xs, 'Đang tải lên / Đang xử lý', '02a-uploading')

      await mockLogin(page, 'lan@congty.vn', 'all_failed')
      await expect(page.getByText('Chưa có tài liệu nào dùng được', { exact: true }).first()).toBeVisible()
      await page.getByRole('button', { name: 'Mở danh sách tài liệu', exact: true }).first().click()
      await expect(page.getByText('Scan_01.pdf').first()).toBeVisible()
      await scan(xs, 'Danh sách có lỗi (all_failed)', '02b-all-failed')
      await showAxePanel(page, 'Bước 2', xs)
      assertClean(xs)
      return summary(xs)
    })
    await hideAxePanel(page)

    await run('axe-chat', async () => {
      const xs: AxeState[] = []
      await mockLogin(page, 'lan@congty.vn', 'answered')
      await ready(page)
      const card = await ask(page, 'Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?')
      await expect(card).toHaveAttribute('aria-busy', 'true')
      await scan(xs, 'Đang tạo câu trả lời', '03a-streaming')
      await expect(card).toHaveAttribute('data-state', 'answered', { timeout: 15_000 })
      await scan(xs, 'Có trích dẫn', '03b-answered')

      for (const [scenario, state, label, file] of [
        ['not_found', 'notfound', 'Không tìm thấy', '03c-not-found'],
        ['partial', 'partial', 'Một phần', '03d-partial'],
        ['llm_error', 'failed', 'Lỗi dịch vụ AI', '03e-llm-error'],
        ['interrupted', 'interrupted', 'Gián đoạn', '03f-interrupted'],
      ] as const) {
        await mockLogin(page, 'lan@congty.vn', scenario)
        await ready(page)
        const c = await ask(page, 'Câu hỏi kiểm thử khả năng tiếp cận')
        await expect(c).toHaveAttribute('data-state', state, { timeout: 15_000 })
        await scan(xs, label, file)
      }
      await showAxePanel(page, 'Bước 3', xs)
      assertClean(xs)
      return summary(xs)
    })
    await hideAxePanel(page)

    let sourceViewerMissing = ''
    let step4Failed = false
    await run(
      'axe-dialogs',
      async () => {
        const xs: AxeState[] = []
        // Bảng Xem nguồn (T18): bấm chip trích dẫn
        await mockLogin(page, 'lan@congty.vn', 'answered')
        await ready(page)
        const card = await ask(page, 'Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?')
        await expect(card).toHaveAttribute('data-state', 'answered', { timeout: 15_000 })
        await card.getByRole('button', { name: /^Trích dẫn 1:/ }).click()
        const viewer = page.locator('[data-testid="source-viewer"], [role="dialog"], [role="complementary"]').filter({ hasText: 'HopDong_A.pdf' })
        if (await viewer.first().isVisible({ timeout: 2000 }).catch(() => false)) await scan(xs, 'Bảng Xem nguồn', '04a-source-viewer')
        else {
          sourceViewerMissing = 'Bấm chip [1] không mở bảng Xem nguồn — T18 (features/source-viewer) chưa implement'
          await page.screenshot({ path: `${extraDir}/04a-source-viewer-missing.png` })
        }

        // Hộp thoại: ứng dụng hiện chưa có hộp thoại trong luồng chính → quét component Dialog ở /dev/ui (chế độ dev)
        await page.goto('/dev/ui')
        await page.getByRole('button', { name: 'Mở hộp thoại' }).click()
        await expect(page.getByRole('dialog')).toBeVisible()
        await scan(xs, 'Hộp thoại (Dialog, /dev/ui)', '04b-dialog')
        await page.keyboard.press('Escape')

        await page.goto('/khong-ton-tai')
        await expect(page.getByText('404').first()).toBeVisible()
        await scan(xs, 'Trang 404', '04c-404')
        await page.goto('/403')
        await expect(page.getByText('403').first()).toBeVisible()
        await scan(xs, 'Trang 403', '04d-403')
        await showAxePanel(page, 'Bước 4', xs)
        if (xs.some((s) => blocking(s) > 0)) step4Failed = true
        assertClean(xs)
        if (sourceViewerMissing) throw new Error(sourceViewerMissing)
        return summary(xs)
      },
      async () =>
        !step4Failed && sourceViewerMissing
          ? `BỊ CHẶN một phần: ${sourceViewerMissing}. Các trạng thái còn lại của bước 4 (hộp thoại, 404, 403) đạt 0 serious/critical: ${summary(all.slice(-3))}`
          : undefined,
    )
  } finally {
    writeFileSync(`${reportDir()}/axe-report.json`, JSON.stringify({ tags: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'], states: all }, null, 2))
    const video = await saveVideo(page)
    writeResult('TC-A11Y-01', {
      status: overall(steps, 4),
      mode: 'mock',
      browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
      axe_totals: all.reduce(
        (acc, s) => {
          for (const k of Object.keys(acc)) acc[k] += s.counts[k]
          return acc
        },
        { critical: 0, serious: 0, moderate: 0, minor: 0 } as Record<string, number>,
      ),
      steps,
      video: video ?? null,
      defects: [],
    })
  }
  expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
})
