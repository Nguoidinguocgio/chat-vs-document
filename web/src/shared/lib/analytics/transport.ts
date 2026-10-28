import { request } from '@/shared/api'

export interface WireEvent {
  name: string
  ts: string
  props: Record<string, number | boolean | string>
}

/**
 * Gửi một lô qua `fetch keepalive` (không dùng `sendBeacon` vì không đặt được header
 * `X-Requested-With`). Máy chủ tự gắn user_id, session_id, role, app_version.
 */
export async function sendBatch(events: WireEvent[]): Promise<void> {
  await request('POST', '/api/analytics/events', { body: { events }, keepalive: true, silent: true })
}
