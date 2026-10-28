import type { Scenario } from './scenario'
import { ANSWERS } from './data'

export interface SseFrame {
  event: string
  data: unknown
  id?: string
}

export const DELTA_INTERVAL_MS = 45

export function encodeFrame(f: SseFrame): string {
  return `${f.id ? `id: ${f.id}\n` : ''}event: ${f.event}\ndata: ${JSON.stringify(f.data)}\n\n`
}

/**
 * Phát các khung SSE cách nhau `intervalMs`. `endAbruptly`: đóng luồng không có done/error
 * (giả lập mất kết nối giữa chừng).
 */
export function sseStream(frames: SseFrame[], intervalMs = DELTA_INTERVAL_MS, endAbruptly = false): ReadableStream<Uint8Array> {
  const enc = new TextEncoder()
  let timer: ReturnType<typeof setTimeout> | undefined
  return new ReadableStream({
    start(controller) {
      let i = 0
      const tick = () => {
        if (i >= frames.length) {
          if (endAbruptly) controller.error(new TypeError('network error'))
          else controller.close()
          return
        }
        controller.enqueue(enc.encode(encodeFrame(frames[i++])))
        timer = setTimeout(tick, intervalMs)
      }
      tick()
    },
    cancel() {
      clearTimeout(timer)
    },
  })
}

/** Tách câu trả lời thành các delta ngắn như LLM thật. */
function deltas(text: string): SseFrame[] {
  return (text.match(/\S+\s*/g) ?? []).map((t) => ({ event: 'delta', data: { text: t } }))
}

/**
 * Chuỗi sự kiện hỏi đáp theo kịch bản: meta → status → delta… → citation… → done, hoặc error.
 * Luôn kết thúc bằng đúng một done/error (trừ `interrupted`: luồng đứt giữa chừng).
 */
export function answerFrames(scenario: Scenario, messageId: string): { frames: SseFrame[]; endAbruptly: boolean } {
  const start: SseFrame[] = [
    { event: 'meta', data: { message_id: messageId, lang: 'vi' } },
    { event: 'status', data: { phase: 'retrieving' } },
  ]
  switch (scenario) {
    case 'not_found':
      return { frames: [...start, { event: 'done', data: { answer_type: 'not_found', missing: null } }], endAbruptly: false }
    case 'partial': {
      const a = ANSWERS.partial
      return {
        frames: [...start, ...deltas(a.text), ...a.citations.map((c) => ({ event: 'citation', data: c })), { event: 'done', data: { answer_type: 'partial', missing: a.missing } }],
        endAbruptly: false,
      }
    }
    case 'interrupted':
      return { frames: [...start, ...deltas(ANSWERS.answered.text).slice(0, 12)], endAbruptly: true }
    case 'timeout':
      return { frames: [...start, { event: 'error', data: { code: 'timeout' } }], endAbruptly: false }
    case 'llm_error':
      return { frames: [...start, ...deltas(ANSWERS.answered.text).slice(0, 5), { event: 'error', data: { code: 'llm_error' } }], endAbruptly: false }
    default: {
      const a = ANSWERS.answered
      return {
        frames: [...start, ...deltas(a.text), ...a.citations.map((c) => ({ event: 'citation', data: c })), { event: 'done', data: { answer_type: 'answered', missing: null } }],
        endAbruptly: false,
      }
    }
  }
}
