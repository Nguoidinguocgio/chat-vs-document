import type { Citation, Message } from '@/shared/api'

/**
 * Trạng thái một câu trả lời (ST-02). Nhánh thành công do T16 nối; `stopped`, `interrupted`,
 * `timeout`, `failed` đã có chỗ trong kiểu để T19 hoàn thiện hành vi.
 */
export type AnswerState = 'searching' | 'streaming' | 'answered' | 'partial' | 'notfound' | 'stopped' | 'interrupted' | 'timeout' | 'failed'

export const TERMINAL: ReadonlySet<AnswerState> = new Set(['answered', 'partial', 'notfound', 'stopped', 'interrupted', 'timeout', 'failed'])

export interface Turn {
  /** Khóa cục bộ (có ngay khi gửi, trước khi máy chủ trả `message_id`). */
  key: string
  messageId: string | null
  question: string
  answer: string
  lang: 'vi' | 'en'
  state: AnswerState
  citations: Record<number, Citation>
  missing: string | null
  errorCode: string | null
}

export type AnswerAction =
  | { type: 'meta'; messageId: string; lang: 'vi' | 'en' }
  | { type: 'delta'; text: string }
  | { type: 'citation'; citation: Citation }
  | { type: 'done'; answerType: 'answered' | 'partial' | 'not_found'; missing: string | null }
  | { type: 'error'; code: string }
  /** Luồng đóng mà chưa có done/error. */
  | { type: 'closed' }
  | { type: 'timeout' }
  | { type: 'stopped' }

let seq = 0

export function newTurn(question: string): Turn {
  return { key: `local-${++seq}`, messageId: null, question, answer: '', lang: 'vi', state: 'searching', citations: {}, missing: null, errorCode: null }
}

const DONE_STATE = { answered: 'answered', partial: 'partial', not_found: 'notfound' } as const

/** Hàm thuần: áp một sự kiện vào lượt hỏi đáp. Sự kiện đến sau trạng thái kết thúc bị bỏ qua. */
export function reduce(turn: Turn, a: AnswerAction): Turn {
  if (TERMINAL.has(turn.state)) {
    // citation đến muộn sau done vẫn hợp lệ (T16 mục 7)
    return a.type === 'citation' ? { ...turn, citations: { ...turn.citations, [a.citation.index]: a.citation } } : turn
  }
  switch (a.type) {
    case 'meta':
      return { ...turn, messageId: a.messageId, lang: a.lang }
    case 'delta':
      return a.text ? { ...turn, answer: turn.answer + a.text, state: 'streaming' } : turn
    case 'citation':
      return { ...turn, citations: { ...turn.citations, [a.citation.index]: a.citation } }
    case 'done':
      return { ...turn, state: DONE_STATE[a.answerType], missing: a.missing ?? null }
    case 'error':
      return { ...turn, state: a.code === 'timeout' ? 'timeout' : 'failed', errorCode: a.code }
    case 'closed':
      return { ...turn, state: 'interrupted' }
    case 'timeout':
      return { ...turn, state: 'timeout', errorCode: 'timeout' }
    case 'stopped':
      return { ...turn, state: 'stopped' }
  }
}

/** Lượt hỏi đáp đã lưu ở máy chủ → trạng thái hiển thị. */
export function fromMessage(m: Message): Turn {
  let state: AnswerState
  if (m.status === 'completed') state = DONE_STATE[m.answer_type ?? 'answered']
  else if (m.status === 'stopped') state = 'stopped'
  else if (m.status === 'failed') state = m.error_code === 'timeout' ? 'timeout' : 'failed'
  else state = 'interrupted' // đang stream ở tab/phiên khác
  return {
    key: m.id,
    messageId: m.id,
    question: m.question,
    answer: m.answer,
    lang: m.lang,
    state,
    citations: Object.fromEntries(m.citations.map((c) => [c.index, c])),
    missing: m.missing,
    errorCode: m.error_code,
  }
}

/** Chỉ số `[n]` thực sự xuất hiện trong văn bản và có dữ liệu trích dẫn (SourceList, T16 mục 3.5). */
export function usedCitations(turn: Pick<Turn, 'answer' | 'citations'>): number[] {
  const seen = new Set<number>()
  for (const m of turn.answer.matchAll(/\[(\d{1,3})\]/g)) {
    const n = Number(m[1])
    if (turn.citations[n]) seen.add(n)
  }
  return [...seen]
}
