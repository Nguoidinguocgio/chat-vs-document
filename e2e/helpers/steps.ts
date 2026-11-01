import { execSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { expect, type Page } from '@playwright/test'
import { reportDir, shot } from './report'

export type Status = 'passed' | 'failed' | 'blocked'
export type Step = { n: number; status: Status; screenshot: string | null; note: string }

export const MOCK_PASSWORD = 'Matkhau-2026'
export const CHAT_URL = /^http:\/\/localhost:5173\/c(\/[^?]*)?(\?.*)?$/ // neo từ đầu: không khớp /login?next=/c
export const LOGIN_URL = /^http:\/\/localhost:5173\/login(\?.*)?$/

export function commit(): string {
  try {
    const head = execSync('git rev-parse --short HEAD', { encoding: 'utf8' }).trim()
    const dirty = execSync('git status --porcelain', { encoding: 'utf8' }).trim() !== ''
    return dirty ? `${head} (+ thay đổi chưa commit)` : head
  } catch {
    return 'unknown'
  }
}

/**
 * Bộ chạy bước như TC-AUTH-01: chạy kiểm tra, luôn chụp ảnh, ghi trạng thái.
 * `blockedIf` trả lý do khi bước không kiểm được ở chế độ này (→ Bị chặn, không tính Đạt).
 * Lỗi của bước được ghi lại rồi chạy tiếp các bước sau (để có đủ ảnh); `steps.failed` cho biết kết quả.
 */
export function stepRunner(page: Page) {
  const steps: Step[] = []
  let current = 0
  const run = async (slug: string, check: () => Promise<string>, blockedIf?: () => Promise<string | undefined>) => {
    current += 1
    let status: Status = 'passed'
    let note = ''
    try {
      note = await check()
    } catch (e) {
      const reason = blockedIf ? await blockedIf() : undefined
      if (reason) {
        status = 'blocked'
        note = reason
      } else {
        status = 'failed'
        note = String(e instanceof Error ? e.message : e)
          // eslint-disable-next-line no-control-regex
          .replace(/\x1b\[[0-9;]*m/g, '')
          .split('\n').filter(Boolean).slice(0, 3).join(' | ')
      }
    }
    let file: string | null = null
    if (!page.isClosed()) file = await shot(page, current, slug)
    steps.push({ n: current, status, screenshot: file, note })
    return status
  }
  return { steps, run }
}

export function overall(steps: Step[], expected: number): Status {
  if (steps.some((s) => s.status === 'failed') || steps.length < expected) return 'failed'
  if (steps.some((s) => s.status === 'blocked')) return 'blocked'
  return 'passed'
}

export function writeResult(tc: string, data: Record<string, unknown>): void {
  mkdirSync(reportDir(), { recursive: true })
  writeFileSync(
    `${reportDir()}/result.json`,
    JSON.stringify({ test_case: tc, round: Number(/round-(\d+)/.exec(process.env.REPORT_DIR ?? '')?.[1] ?? 1), ...data, run_at: new Date().toISOString(), runner: 'Claude Code (Playwright tự động)', commit: commit() }, null, 2),
  )
}

/** Mở app với kịch bản API giả rồi đăng nhập; chờ tới khi vào trang chat (hoặc trang đổi mật khẩu). */
export async function mockLogin(page: Page, email: string, scenario = 'answered', expectUrl: RegExp = CHAT_URL): Promise<void> {
  await page.goto(`/?mock=${scenario}`)
  await expect(page).toHaveURL(LOGIN_URL)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(MOCK_PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await expect(page).toHaveURL(expectUrl)
}

export const composer = (page: Page) => page.getByRole('textbox', { name: 'Câu hỏi', exact: true })

/** Gõ câu hỏi và bấm Gửi; trả về thẻ câu trả lời mới nhất. */
export async function ask(page: Page, question: string) {
  await expect(composer(page)).toBeEnabled()
  await composer(page).fill(question)
  await page.getByTestId('send').click()
  return page.getByTestId('answer-card').last()
}
