import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockState } from '@/mocks'
import { server } from '@/mocks/server'
import { ApiError, request, setGlobalErrorHandler } from '@/shared/api'
import { clearQueue, flush, pendingCount, track } from '@/shared/lib/analytics'
import { clearAllDrafts, DRAFT_DEBOUNCE_MS, flushDrafts, loadDraft, saveDraft } from '@/shared/lib/draft'
import { formatBytes, formatDateTime, midEllipsis } from '@/shared/lib/format'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  setGlobalErrorHandler(() => {})
})
afterAll(() => server.close())
beforeEach(() => {
  resetMockState('lan@congty.vn')
  sessionStorage.clear()
  clearQueue()
})

describe('shared/api request', () => {
  it('thêm X-Requested-With cho POST, không thêm cho GET; gửi cookie cùng origin', async () => {
    const seen: Record<string, string | null> = {}
    server.use(
      http.get('*/api/x', ({ request: r }) => {
        seen.get = r.headers.get('X-Requested-With')
        return HttpResponse.json({ ok: 1 })
      }),
      http.post('*/api/x', ({ request: r }) => {
        seen.post = r.headers.get('X-Requested-With')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    expect(await request('GET', '/api/x')).toEqual({ ok: 1 })
    expect(await request('POST', '/api/x', { body: {} })).toBeUndefined()
    expect(seen).toEqual({ get: null, post: 'fetch' })
  })

  it('chuẩn hóa lỗi chuẩn thành ApiError; không phải JSON → NETWORK_OR_UNKNOWN', async () => {
    server.use(
      http.get('*/api/a', () => HttpResponse.json({ error: { code: 'DOCUMENT_LIMIT_REACHED', message_key: 'documents.limit_reached', details: { limit: 20 } } }, { status: 409 })),
      http.get('*/api/b', () => new HttpResponse('<html>proxy</html>', { status: 502 })),
    )
    await expect(request('GET', '/api/a')).rejects.toMatchObject({ status: 409, code: 'DOCUMENT_LIMIT_REACHED', messageKey: 'documents.limit_reached', details: { limit: 20 } })
    await expect(request('GET', '/api/b')).rejects.toMatchObject({ status: 502, code: 'NETWORK_OR_UNKNOWN', messageKey: 'errors.generic' })
  })

  it('gọi bộ xử lý toàn cục cho lỗi phiên/quyền, trừ khi silent', async () => {
    const handler = vi.fn()
    setGlobalErrorHandler(handler)
    server.use(http.get('*/api/s', () => HttpResponse.json({ error: { code: 'SESSION_EXPIRED', message_key: 'errors.session_expired', details: {} } }, { status: 401 })))
    await expect(request('GET', '/api/s')).rejects.toBeInstanceOf(ApiError)
    await expect(request('GET', '/api/s', { silent: true })).rejects.toBeInstanceOf(ApiError)
    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler.mock.calls[0][0].code).toBe('SESSION_EXPIRED')
  })
})

describe('shared/lib/draft', () => {
  it('debounce 500 ms rồi lưu sessionStorage theo user + hội thoại', () => {
    vi.useFakeTimers()
    try {
      saveDraft('u1', 'c1', 'xin chào')
      expect(sessionStorage.getItem('draft:u1:c1')).toBeNull()
      expect(loadDraft('u1', 'c1')).toBe('xin chào')
      vi.advanceTimersByTime(DRAFT_DEBOUNCE_MS)
      expect(sessionStorage.getItem('draft:u1:c1')).toBe('xin chào')
      expect(loadDraft('u2', 'c1')).toBe('')
    } finally {
      vi.useRealTimers()
    }
  })
  it('flushDrafts lưu ngay; clearAllDrafts chỉ xóa khóa draft:', () => {
    sessionStorage.setItem('khac', '1')
    saveDraft('u1', 'c2', 'abc')
    flushDrafts()
    expect(sessionStorage.getItem('draft:u1:c2')).toBe('abc')
    clearAllDrafts()
    expect(sessionStorage.getItem('draft:u1:c2')).toBeNull()
    expect(sessionStorage.getItem('khac')).toBe('1')
  })
  it('không bao giờ dùng localStorage', () => {
    saveDraft('u1', 'c3', 'x')
    flushDrafts()
    expect(Object.keys(localStorage)).toEqual([])
  })
})

describe('shared/lib/analytics', () => {
  it('loại sự kiện có chuỗi dài, chuỗi tự do, giá trị ngoài enum, thuộc tính lạ', () => {
    expect(track('chat_answer_first_token', { question_id: 'a'.repeat(65), ttft_ms: 1 })).toBe(false)
    expect(track('citation_clicked', { question_id: 'Thời hạn thanh toán là bao lâu?' })).toBe(false)
    expect(track('client_network_lost', { context: 'khac' })).toBe(false)
    expect(track('client_network_lost', { context: 'idle', question: 'x' })).toBe(false)
    expect(track('chat_answer_first_token', { question_id: 'q-1', ttft_ms: -5 })).toBe(false)
    // @ts-expect-error tên ngoài danh sách trắng
    expect(track('tu_dat_ten', {})).toBe(false)
    expect(pendingCount()).toBe(0)
  })
  it('gửi lô hợp lệ tới /api/analytics/events', async () => {
    let body: unknown
    server.use(
      http.post('*/api/analytics/events', async ({ request: r }) => {
        body = await r.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    expect(track('chat_answer_first_token', { question_id: 'q-1', ttft_ms: 820 })).toBe(true)
    expect(track('client_network_restored', { context: 'streaming', duration_ms: 3000 })).toBe(true)
    await flush()
    expect(pendingCount()).toBe(0)
    expect(body).toMatchObject({ events: [{ name: 'chat_answer_first_token', props: { question_id: 'q-1', ttft_ms: 820 } }, { name: 'client_network_restored' }] })
  })
})

describe('shared/lib/format', () => {
  it('formatDateTime theo giờ Việt Nam dd/MM/yyyy HH:mm', () => {
    expect(formatDateTime('2026-10-23T02:02:00Z')).toBe('23/10/2026 09:02')
    expect(formatDateTime('2026-10-23T17:30:00Z')).toBe('24/10/2026 00:30')
  })
  it('formatBytes', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1536)).toBe('1,5 KB')
    expect(formatBytes(10 * 1024 * 1024)).toBe('10 MB')
  })
  it('midEllipsis giữ đầu và đuôi file', () => {
    const s = midEllipsis('BaoCao_Q3_2026_ban_cuoi_cung_da_duyet_v3.pdf')
    expect(Array.from(s)).toHaveLength(34)
    expect(s).toContain('…')
    expect(s.endsWith('_v3.pdf')).toBe(true)
    expect(midEllipsis('ngan.pdf')).toBe('ngan.pdf')
  })
})
