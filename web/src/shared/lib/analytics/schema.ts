// Danh sách trắng sự kiện client (PRD §6.2) — khớp AnalyticsSchema.CLIENT_EVENTS của backend.
// Thuộc tính chỉ là số, boolean, ID (≤ 64 ký tự [0-9A-Za-z_-]) hoặc enum; không bao giờ văn bản tự do.
type PropType = 'num' | 'bool' | 'id' | readonly string[]

const OUTCOME = ['answered', 'partial', 'not_found', 'stopped', 'failed', 'timeout', 'llm_error', 'rate_limited', 'network'] as const
const NETWORK_CONTEXT = ['upload', 'streaming', 'idle'] as const

export const CLIENT_EVENTS = {
  chat_answer_first_token: { question_id: 'id', ttft_ms: 'num' }, // EV-021
  chat_answer_stopped: { question_id: 'id', elapsed_ms: 'num' }, // EV-022
  chat_answer_regenerated: { question_id: 'id', previous_outcome: OUTCOME }, // EV-025
  citation_clicked: { question_id: 'id', document_id: 'id', page: 'num', citation_index: 'num', viewer_load_ms: 'num' }, // EV-030
  client_network_lost: { context: NETWORK_CONTEXT, duration_ms: 'num' }, // EV-050
  client_network_restored: { context: NETWORK_CONTEXT, duration_ms: 'num' },
} as const satisfies Record<string, Record<string, PropType>>

export type ClientEventName = keyof typeof CLIENT_EVENTS
export type PropValue = number | boolean | string

const ID = /^[0-9A-Za-z_-]{1,64}$/

function accepts(type: PropType, v: PropValue): boolean {
  if (type === 'num') return typeof v === 'number' && Number.isFinite(v) && v >= 0
  if (type === 'bool') return typeof v === 'boolean'
  if (type === 'id') return typeof v === 'string' && ID.test(v)
  return typeof v === 'string' && type.includes(v)
}

/** Lý do vi phạm, hoặc null nếu hợp lệ. */
export function violation(name: string, props: Record<string, PropValue>): string | null {
  const allowed = (CLIENT_EVENTS as Record<string, Record<string, PropType>>)[name]
  if (!allowed) return 'unknown_event'
  for (const [k, v] of Object.entries(props)) {
    if (!(k in allowed)) return `unknown_prop:${k}`
    if (!accepts(allowed[k], v)) return `invalid_value:${k}`
  }
  return null
}
