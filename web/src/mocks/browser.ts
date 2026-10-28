import { setupWorker } from 'msw/browser'
import { handlers, initScenarioFromUrl, resetMockState } from './index'

/** Bật API giả trong trình duyệt (chế độ `npm run dev:mock`). */
export async function startMockWorker(): Promise<void> {
  initScenarioFromUrl()
  resetMockState()
  await setupWorker(...handlers).start({ onUnhandledRequest: 'bypass', quiet: true })
}
