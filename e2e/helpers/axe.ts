import { resolve } from 'node:path'
import type { Page } from '@playwright/test'

// axe-core lấy từ devDependencies của web/ (không cần thêm @axe-core/playwright).
const AXE_PATH = resolve(__dirname, '../../web/node_modules/axe-core/axe.min.js')
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice']

export type AxeViolation = { id: string; impact: string | null; help: string; nodes: { target: string[]; html: string }[] }
export type AxeState = { state: string; url: string; violations: AxeViolation[]; counts: Record<string, number> }

/** Chạy axe trên toàn trang (WCAG 2.1 A/AA + best-practice) và tóm tắt theo mức độ. */
export async function runAxe(page: Page, state: string): Promise<AxeState> {
  if (!(await page.evaluate(() => 'axe' in window))) await page.addScriptTag({ path: AXE_PATH })
  const raw = await page.evaluate(
    async (tags) =>
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (await (window as any).axe.run(document, { runOnly: { type: 'tag', values: tags }, resultTypes: ['violations'] })).violations,
    WCAG_TAGS,
  )
  const violations: AxeViolation[] = (raw as AxeViolation[]).map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.map((n) => ({ target: n.target, html: n.html.slice(0, 200) })),
  }))
  const counts: Record<string, number> = { critical: 0, serious: 0, moderate: 0, minor: 0 }
  for (const v of violations) counts[v.impact ?? 'minor'] += v.nodes.length
  return { state, url: page.url(), violations, counts }
}

export const blocking = (s: AxeState) => s.counts.critical + s.counts.serious

/** Bảng tóm tắt kết quả axe đặt góc màn hình để ảnh chụp/video có bằng chứng; gỡ bằng {@link hideAxePanel}. */
export async function showAxePanel(page: Page, title: string, states: AxeState[]): Promise<void> {
  const rows = states.map((s) => ({
    state: s.state,
    text: `critical ${s.counts.critical} · serious ${s.counts.serious} · moderate ${s.counts.moderate} · minor ${s.counts.minor}`,
    bad: blocking(s) > 0,
    rules: s.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious').map((v) => `${v.impact}: ${v.id}`),
  }))
  await page.evaluate(
    ({ title, rows }) => {
      document.getElementById('__axe_panel')?.remove()
      const el = document.createElement('div')
      el.id = '__axe_panel'
      el.setAttribute('aria-hidden', 'true')
      el.style.cssText =
        'position:fixed;left:12px;bottom:12px;z-index:2147483647;max-width:620px;background:#111827;color:#f9fafb;font:12px/1.45 Consolas,monospace;padding:10px 12px;border-radius:8px;box-shadow:0 4px 18px rgba(0,0,0,.4);opacity:.95'
      const h = document.createElement('div')
      h.textContent = `axe-core — ${title}`
      h.style.cssText = 'font-weight:bold;margin-bottom:4px'
      el.appendChild(h)
      for (const r of rows) {
        const line = document.createElement('div')
        line.textContent = `${r.bad ? '✗' : '✓'} ${r.state}: ${r.text}${r.rules.length ? ` [${r.rules.join(', ')}]` : ''}`
        line.style.color = r.bad ? '#fca5a5' : '#86efac'
        el.appendChild(line)
      }
      document.body.appendChild(el)
    },
    { title, rows },
  )
}

export async function hideAxePanel(page: Page): Promise<void> {
  await page.evaluate(() => document.getElementById('__axe_panel')?.remove())
}
