// Gửi sự kiện analytics (T05 mục 3.3) — nơi duy nhất được gửi analytics (luật R-6).
import { CLIENT_EVENTS, violation, type ClientEventName, type PropValue } from './schema'
import { sendBatch, type WireEvent } from './transport'

export { CLIENT_EVENTS, type ClientEventName }

export const FLUSH_INTERVAL_MS = 10_000
const MAX_BATCH = 50

let queue: WireEvent[] = []
let timer: ReturnType<typeof setInterval> | undefined

/**
 * Ghi một sự kiện vào hàng đợi. Sự kiện/thuộc tính ngoài danh sách trắng bị bỏ (cảnh báo ở dev)
 * — không bao giờ gửi nội dung câu hỏi/câu trả lời/tài liệu.
 */
export function track(name: ClientEventName, props: Record<string, PropValue> = {}): boolean {
  const bad = violation(name, props)
  if (bad) {
    if (import.meta.env.DEV) console.warn(`[analytics] bỏ sự kiện ${name}: ${bad}`)
    return false
  }
  queue.push({ name, ts: new Date().toISOString(), props })
  return true
}

/** Gửi mọi sự kiện đang chờ (theo lô ≤ 50). Lỗi mạng: bỏ lô, không thử lại vô hạn. */
export async function flush(): Promise<void> {
  while (queue.length > 0) {
    const batch = queue.slice(0, MAX_BATCH)
    queue = queue.slice(MAX_BATCH)
    try {
      await sendBatch(batch)
    } catch {
      // analytics không được làm hỏng trải nghiệm
    }
  }
}

function onVisibility(): void {
  if (document.visibilityState === 'hidden') void flush()
}

/** Bật gửi định kỳ 10 s và khi tab bị ẩn. */
export function startAnalytics(): void {
  if (timer) return
  timer = setInterval(() => void flush(), FLUSH_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibility)
}

export function stopAnalytics(): void {
  clearInterval(timer)
  timer = undefined
  document.removeEventListener('visibilitychange', onVisibility)
}

/** Số sự kiện đang chờ (cho test). */
export function pendingCount(): number {
  return queue.length
}

export function clearQueue(): void {
  queue = []
}
