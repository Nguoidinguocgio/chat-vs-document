import { render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { groupConversations, groupOf } from '@/features/conversations'
import type { Citation, Message } from '@/shared/api'
import { i18n } from '@/shared/lib/i18n'
import { fromMessage, newTurn, reduce, usedCitations, type AnswerAction, type Turn } from './answerReducer'
import MessageList from './MessageList.vue'
import { composerState, type ComposerInput } from './useComposerState'

const ready = { total: 3, hasReady: true, processing: 0 }
const base: ComposerInput = { online: true, summary: ready, busy: false, text: 'Câu hỏi?' }

describe('composerState (ST-04)', () => {
  it.each<[string, Partial<ComposerInput>, string, boolean, boolean]>([
    ['offline', { online: false }, 'offline', false, false],
    ['không có tài liệu', { summary: { total: 0, hasReady: false, processing: 0 } }, 'no_documents', true, false],
    ['chỉ đang xử lý', { summary: { total: 2, hasReady: false, processing: 2 } }, 'processing_only', true, false],
    ['tất cả lỗi', { summary: { total: 2, hasReady: false, processing: 0 } }, 'all_failed', true, false],
    ['đang trả lời', { busy: true }, 'busy', false, false],
    ['rỗng', { text: '' }, 'empty', false, false],
    ['chỉ khoảng trắng', { text: '  \n\t ' }, 'empty', false, false],
    ['2.001 ký tự', { text: 'a'.repeat(2001) }, 'too_long', false, false],
    ['2.000 ký tự', { text: 'a'.repeat(2000) }, 'ready', false, true],
    ['bình thường', {}, 'ready', false, true],
  ])('%s → %s', (_n, patch, code, locked, canSend) => {
    expect(composerState({ ...base, ...patch })).toEqual({ code, locked, canSend })
  })

  it('thứ tự ưu tiên: offline thắng quá dài/không tài liệu; không tài liệu thắng busy', () => {
    expect(composerState({ ...base, online: false, text: 'a'.repeat(3000) }).code).toBe('offline')
    expect(composerState({ ...base, online: false, summary: { total: 0, hasReady: false, processing: 0 } }).code).toBe('offline')
    expect(composerState({ ...base, busy: true, summary: { total: 0, hasReady: false, processing: 0 } }).code).toBe('no_documents')
    expect(composerState({ ...base, busy: true, text: 'a'.repeat(3000) }).code).toBe('busy')
  })

  it('danh sách tài liệu chưa tải xong: không khóa ô nhập', () => {
    expect(composerState({ ...base, documentsLoaded: false, summary: { total: 0, hasReady: false, processing: 0 } }).code).toBe('ready')
  })
})

const cite = (index: number, page = index): Citation => ({ index, document_id: `00000000-0000-4000-8000-00000000000${index}`, document_name: `D${index}.pdf`, page, document_deleted: false })

describe('answerReducer (ST-02)', () => {
  const run = (...actions: AnswerAction[]) => actions.reduce(reduce, newTurn('q'))

  it('searching → streaming (delta đầu) → answered/partial/notfound theo done', () => {
    const t0 = newTurn('q')
    expect(t0.state).toBe('searching')
    const t1 = reduce(reduce(t0, { type: 'meta', messageId: 'm', lang: 'en' }), { type: 'delta', text: 'Hi [1]' })
    expect([t1.state, t1.messageId, t1.lang]).toEqual(['streaming', 'm', 'en'])
    expect(reduce(t1, { type: 'done', answerType: 'answered', missing: null }).state).toBe('answered')
    expect(reduce(t1, { type: 'done', answerType: 'partial', missing: 'x' })).toMatchObject({ state: 'partial', missing: 'x' })
    expect(run({ type: 'done', answerType: 'not_found', missing: null }).state).toBe('notfound')
  })

  it('error/closed/timeout/stopped có chỗ trong máy trạng thái; sự kiện sau kết thúc bị bỏ, trừ citation muộn', () => {
    expect(run({ type: 'error', code: 'timeout' }).state).toBe('timeout')
    expect(run({ type: 'error', code: 'llm_error' })).toMatchObject({ state: 'failed', errorCode: 'llm_error' })
    expect(run({ type: 'delta', text: 'a' }, { type: 'closed' }).state).toBe('interrupted')
    expect(run({ type: 'timeout' }).state).toBe('timeout')
    expect(run({ type: 'stopped' }).state).toBe('stopped')
    const done = run({ type: 'delta', text: 'a [1]' }, { type: 'done', answerType: 'answered', missing: null }, { type: 'delta', text: 'X' }, { type: 'closed' })
    expect(done).toMatchObject({ state: 'answered', answer: 'a [1]' })
    expect(reduce(done, { type: 'citation', citation: cite(1) }).citations[1]).toEqual(cite(1))
  })

  it('usedCitations: chỉ [n] xuất hiện trong văn bản và có dữ liệu', () => {
    const t = run({ type: 'delta', text: 'x [2] y [1] z [2] [7]' }, { type: 'citation', citation: cite(1) }, { type: 'citation', citation: cite(2) }, { type: 'citation', citation: cite(3) })
    expect(usedCitations(t)).toEqual([2, 1])
  })

  it('fromMessage: trạng thái lưu ở máy chủ → trạng thái hiển thị', () => {
    const m: Message = { id: 'm1', question: 'q', answer: 'a', lang: 'vi', status: 'completed', answer_type: 'not_found', missing: null, error_code: null, citations: [cite(1)], created_at: '' }
    expect(fromMessage(m)).toMatchObject({ state: 'notfound', citations: { 1: cite(1) } })
    expect(fromMessage({ ...m, status: 'failed', error_code: 'timeout' }).state).toBe('timeout')
    expect(fromMessage({ ...m, status: 'failed', error_code: 'llm_error' }).state).toBe('failed')
    expect(fromMessage({ ...m, status: 'stopped' }).state).toBe('stopped')
    expect(fromMessage({ ...m, status: 'streaming' }).state).toBe('interrupted')
  })
})

describe('nhóm hội thoại theo giờ Việt Nam', () => {
  // 2026-10-28 09:00 giờ VN = 02:00Z
  const now = new Date('2026-10-28T02:00:00Z')
  it('Hôm nay / 7 ngày trước / 30 ngày trước / Cũ hơn', () => {
    expect(groupOf('2026-10-27T17:30:00Z', now)).toBe('today') // 00:30 ngày 28 giờ VN
    expect(groupOf('2026-10-27T16:30:00Z', now)).toBe('last_7_days') // 23:30 ngày 27 giờ VN
    expect(groupOf('2026-10-21T03:00:00Z', now)).toBe('last_7_days')
    expect(groupOf('2026-10-10T03:00:00Z', now)).toBe('last_30_days')
    expect(groupOf('2026-08-01T03:00:00Z', now)).toBe('older')
    const c = (id: string, at: string) => ({ id, title: id, created_at: at, last_activity_at: at })
    const groups = groupConversations([c('cu', '2026-08-01T03:00:00Z'), c('moi', '2026-10-28T01:00:00Z'), c('tuan', '2026-10-25T01:00:00Z')], now)
    expect(groups.map((g) => [g.group, g.items.map((x) => x.id)])).toEqual([
      ['today', ['moi']],
      ['last_7_days', ['tuan']],
      ['older', ['cu']],
    ])
  })
})

describe('MessageList: cuộn thông minh', () => {
  function turn(answer: string, state: Turn['state'] = 'streaming'): Turn {
    return { ...newTurn('Câu hỏi'), key: 't1', answer, state }
  }

  it('đang cuộn lên đọc → không bị kéo xuống, hiện "↓ Câu trả lời mới"; bấm → về đáy', async () => {
    setActivePinia(createPinia())
    const qc = new QueryClient()
    qc.setQueryData(['documents'], { items: [], count: 0, limit: 20 })
    const { getByTestId, findByTestId, queryByTestId, rerender } = render(MessageList, {
      props: { turns: [turn('a')] },
      global: { plugins: [i18n, [VueQueryPlugin, { queryClient: qc }]] },
    })
    const box = getByTestId('message-list')
    Object.defineProperty(box, 'scrollHeight', { configurable: true, get: () => 1000 })
    Object.defineProperty(box, 'clientHeight', { configurable: true, get: () => 200 })
    let top = 0
    Object.defineProperty(box, 'scrollTop', { configurable: true, get: () => top, set: (v: number) => (top = v) })
    box.scrollTop = 0
    box.dispatchEvent(new Event('scroll'))
    await rerender({ turns: [turn('a b c d')] })
    await nextTick()
    expect(box.scrollTop).toBe(0)
    ;(await findByTestId('jump-new')).click()
    await nextTick()
    expect(box.scrollTop).toBe(1000)
    expect(queryByTestId('jump-new')).toBeNull()

    // ở gần đáy (≤ 60 px) → tự cuộn theo nội dung mới
    box.scrollTop = 750
    box.dispatchEvent(new Event('scroll'))
    await rerender({ turns: [turn('a b c d e f')] })
    await nextTick()
    expect(box.scrollTop).toBe(1000)
  })
})
