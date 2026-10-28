import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import { conversationsApi } from '@/features/conversations'
import { isApiError, openStream, type Citation } from '@/shared/api'
import { track } from '@/shared/lib/analytics'
import { t } from '@/shared/lib/i18n'
import { registerReset } from '@/shared/lib/reset'
import { parseSseStream } from '@/shared/lib/sse'
import { TERMINAL, fromMessage, newTurn, reduce, usedCitations, type AnswerAction, type Turn } from './answerReducer'

/** Im lặng quá lâu (không byte nào, kể cả `: ping`) → `timeout` (T16 mục 3.2). Test được rút ngắn. */
export const streamConfig = { silenceMs: 30_000 }

export interface AskHooks {
  /** Hội thoại vừa được tạo cho câu hỏi đầu tiên (thay URL `/c` → `/c/:id`). */
  onCreated?: (id: string) => void
  /** Máy chủ báo không còn tài liệu sẵn sàng → làm mới tóm tắt tài liệu. */
  onNoDocuments?: () => void
  /** Lượt hỏi đáp kết thúc (làm mới danh sách hội thoại, focus ô nhập). */
  onSettled?: () => void
}

type Frame = (cb: () => void) => void
const nextFrame: Frame = (cb) => (typeof requestAnimationFrame === 'function' ? requestAnimationFrame(() => cb()) : setTimeout(cb, 16))

/**
 * Luồng hỏi đáp (ALG-01): mỗi lúc chỉ một luồng; hủy khi rời hội thoại. `delta` được gộp và áp
 * một lần mỗi khung hình thay vì render theo từng token.
 */
export const useAnswerStream = defineStore('answer-stream', () => {
  const conversationId = ref<string | null>(null)
  const turns = ref<Turn[]>([])
  const loading = ref(false)
  const notFound = ref(false)
  /** Khóa i18n của lỗi trước luồng (hiển thị ở dòng trạng thái ô nhập). */
  const notice = ref<string | null>(null)
  /** Thông báo cho vùng aria-live riêng (không đọc từng token). */
  const announcement = ref('')
  const controller = shallowRef<AbortController | null>(null)

  const busy = computed(() => turns.value.some((x) => !TERMINAL.has(x.state)))

  let abortReason: 'leave' | 'stop' | 'timeout' | null = null
  let loadToken = 0

  function patch(key: string, a: AnswerAction): void {
    const i = turns.value.findIndex((x) => x.key === key)
    if (i < 0) return
    const before = turns.value[i]
    const after = reduce(before, a)
    turns.value[i] = after
    if (!TERMINAL.has(before.state) && TERMINAL.has(after.state)) announcement.value = announce(after)
  }

  function abort(reason: 'leave' | 'stop' | 'timeout'): void {
    if (!controller.value) return
    abortReason = reason
    controller.value.abort()
  }

  /** Mở hội thoại `id` (null = hội thoại mới `/c`). Mở lại hội thoại đang hiển thị: không làm gì. */
  async function open(id: string | null): Promise<void> {
    if (id !== null && id === conversationId.value) return
    abort('leave')
    const token = ++loadToken
    conversationId.value = id
    turns.value = []
    notFound.value = false
    notice.value = null
    if (id === null) return
    loading.value = true
    try {
      const detail = await conversationsApi.get(id)
      if (token === loadToken) turns.value = detail.messages.map(fromMessage)
    } catch (e) {
      if (token === loadToken && isApiError(e) && (e.status === 404 || e.status === 403)) notFound.value = true
    } finally {
      if (token === loadToken) loading.value = false
    }
  }

  async function ask(question: string, hooks: AskHooks = {}): Promise<void> {
    if (busy.value) return
    notice.value = null
    // Thẻ "Đang tìm…" hiện NGAY, không chờ máy chủ (AC-020.1).
    const turn = newTurn(question)
    turns.value.push(turn)
    const key = turn.key
    announcement.value = t('chat.announce_start')
    const ctl = new AbortController()
    controller.value = ctl
    abortReason = null
    const startedAt = performance.now()
    let firstToken = true
    let buffer = ''
    let scheduled = false
    let watchdog: ReturnType<typeof setTimeout> | undefined

    const flush = () => {
      scheduled = false
      if (!buffer) return
      const text = buffer
      buffer = ''
      patch(key, { type: 'delta', text })
    }
    const resetWatchdog = () => {
      clearTimeout(watchdog)
      watchdog = setTimeout(() => abort('timeout'), streamConfig.silenceMs)
    }
    const removeTurn = () => (turns.value = turns.value.filter((x) => x.key !== key))

    try {
      resetWatchdog()
      let id = conversationId.value
      if (id === null) {
        id = (await conversationsApi.create()).id
        conversationId.value = id
        loadToken++
        hooks.onCreated?.(id)
      }
      const body = await openStream(`/api/conversations/${id}/messages`, { question }, ctl.signal)
      for await (const ev of parseSseStream(body, { onActivity: resetWatchdog, signal: ctl.signal })) {
        const d = ev.data as Record<string, unknown>
        if (ev.event === 'delta') {
          if (firstToken) {
            firstToken = false
            const messageId = current(key)?.messageId
            if (messageId) track('chat_answer_first_token', { question_id: messageId, ttft_ms: Math.round(performance.now() - startedAt) })
          }
          buffer += String(d.text ?? '')
          if (!scheduled) {
            scheduled = true
            nextFrame(flush)
          }
          continue
        }
        flush()
        if (ev.event === 'meta') patch(key, { type: 'meta', messageId: String(d.message_id), lang: d.lang === 'en' ? 'en' : 'vi' })
        else if (ev.event === 'citation') patch(key, { type: 'citation', citation: d as unknown as Citation })
        else if (ev.event === 'done') patch(key, { type: 'done', answerType: d.answer_type as 'answered' | 'partial' | 'not_found', missing: (d.missing as string | null) ?? null })
        else if (ev.event === 'error') patch(key, { type: 'error', code: String(d.code) })
      }
      flush()
      patch(key, { type: 'closed' })
    } catch (e) {
      flush()
      if (e instanceof DOMException && e.name === 'AbortError') {
        if (abortReason === 'timeout') patch(key, { type: 'timeout' })
        else if (abortReason === 'stop') patch(key, { type: 'stopped' })
      } else if (isApiError(e, 'QUESTION_INVALID')) {
        removeTurn()
        notice.value = question.trim() ? 'chat.too_long' : 'chat.question_invalid'
      } else if (isApiError(e, 'NO_READY_DOCUMENTS')) {
        removeTurn()
        hooks.onNoDocuments?.()
      } else if (isApiError(e, 'ANSWER_IN_PROGRESS')) {
        removeTurn()
        notice.value = 'chat.answer_in_progress'
      } else if (isApiError(e) && e.code !== 'NETWORK_OR_UNKNOWN' && e.status > 0 && e.status < 500) {
        removeTurn()
      } else {
        patch(key, { type: 'closed' })
      }
    } finally {
      clearTimeout(watchdog)
      if (controller.value === ctl) controller.value = null
      hooks.onSettled?.()
    }
  }

  function current(key: string): Turn | undefined {
    return turns.value.find((x) => x.key === key)
  }

  function announce(x: Turn): string {
    if (x.state === 'notfound') return t('chat.announce_not_found')
    if (x.state === 'answered' || x.state === 'partial') return t('chat.announce_done', { n: usedCitations(x).length })
    return ''
  }

  /** Thử lại tối thiểu (T19 sẽ thay bằng `regenerate`): bỏ lượt lỗi, hỏi lại cùng câu. */
  function retry(key: string, hooks: AskHooks = {}): void {
    const x = current(key)
    if (!x || busy.value) return
    turns.value = turns.value.filter((y) => y.key !== key)
    void ask(x.question, hooks)
  }

  function reset(): void {
    abort('leave')
    conversationId.value = null
    turns.value = []
    notice.value = null
    notFound.value = false
    announcement.value = ''
  }
  registerReset(reset)

  return { conversationId, turns, loading, notFound, notice, announcement, busy, open, ask, abort, retry, reset }
})
