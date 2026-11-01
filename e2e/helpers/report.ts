import { mkdirSync } from 'node:fs'
import type { Page } from '@playwright/test'

const tc = () => process.env.TC_ID ?? 'adhoc'
// REPORT_DIR (tính từ gốc repo, ví dụ test_report/TC-AUTH-05/round-2) cho phép tách từng vòng kiểm thử.
export const reportDir = () => `../${process.env.REPORT_DIR ?? `test_report/${tc()}`}`

/** Chụp ảnh một bước: test_report/<TC_ID>/screenshots/NN-<slug>.png */
export async function shot(page: Page, step: number, slug: string): Promise<string> {
  const dir = `${reportDir()}/screenshots`
  mkdirSync(dir, { recursive: true })
  const name = `${String(step).padStart(2, '0')}-${slug}.png`
  await page.screenshot({ path: `${dir}/${name}` })
  return `screenshots/${name}`
}

/** Đóng trang rồi lưu video về test_report/<TC_ID>/video/<TC_ID>.webm */
export async function saveVideo(page: Page): Promise<string | undefined> {
  const video = page.video()
  await page.close()
  if (!video) return undefined
  const dir = `${reportDir()}/video`
  mkdirSync(dir, { recursive: true })
  const file = `${dir}/${tc()}.webm`
  await video.saveAs(file)
  return `video/${tc()}.webm`
}
