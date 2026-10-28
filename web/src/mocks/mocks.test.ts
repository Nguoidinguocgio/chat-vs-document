// @vitest-environment node
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { SseParser, type SseMessage } from '@/shared/realtime/sseParser'
import { DISABLED_EMAIL, LOCKED_EMAIL, MOCK_PASSWORD, SCENARIOS, mockState, resetMockState, setScenario, type Scenario } from './index'
import { server } from './server'

const BASE = 'http://localhost'
const post = (path: string, body?: unknown) =>
  fetch(BASE + path, { method: 'POST', headers: { 'X-Requested-With': 'fetch', 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) })

async function readSse(res: Response): Promise<{ events: SseMessage[]; broken: boolean }> {
  const parser = new SseParser()
  const reader = res.body!.pipeThrough(new TextDecoderStream()).getReader()
  const events: SseMessage[] = []
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) return { events, broken: false }
      events.push(...parser.push(value))
    }
  } catch {
    return { events, broken: true }
  }
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())
beforeEach(() => {
  setScenario('answered')
  resetMockState()
})

describe('MSW: đăng nhập', () => {
  it('đúng mật khẩu → CurrentUser; sai → INVALID_CREDENTIALS', async () => {
    const ok = await post('/api/auth/login', { email: 'lan@congty.vn', password: MOCK_PASSWORD })
    expect(ok.status).toBe(200)
    expect((await ok.json()).display_name).toBe('Lan')
    const bad = await post('/api/auth/login', { email: 'lan@congty.vn', password: 'sai' })
    expect(bad.status).toBe(401)
    expect((await bad.json()).error.message_key).toBe('auth.invalid_credentials')
  })
  it('khóa tạm 423 kèm retry_after_s; vô hiệu hóa 403', async () => {
    const locked = await post('/api/auth/login', { email: LOCKED_EMAIL, password: MOCK_PASSWORD })
    expect(locked.status).toBe(423)
    expect((await locked.json()).error.details.retry_after_s).toBe(840)
    expect((await post('/api/auth/login', { email: DISABLED_EMAIL, password: MOCK_PASSWORD })).status).toBe(403)
  })
  it('mật khẩu tạm: API khác bị PASSWORD_CHANGE_REQUIRED cho tới khi đổi', async () => {
    resetMockState('moi@congty.vn')
    const blocked = await fetch(BASE + '/api/documents')
    expect((await blocked.json()).error.code).toBe('PASSWORD_CHANGE_REQUIRED')
    expect((await post('/api/auth/change-password', { new_password: 'ngan' })).status).toBe(422)
    expect((await post('/api/auth/change-password', { new_password: 'MatKhauMoi-2027' })).status).toBe(204)
    expect((await fetch(BASE + '/api/documents')).status).toBe(200)
  })
  it('thiếu X-Requested-With → 403; chưa đăng nhập → 401', async () => {
    const r = await fetch(BASE + '/api/auth/login', { method: 'POST', body: '{}' })
    expect(r.status).toBe(403)
    expect((await fetch(BASE + '/api/me')).status).toBe(401)
  })
})

describe('MSW: tài liệu theo kịch bản', () => {
  const counts: Partial<Record<Scenario, number>> = { empty_workspace: 0, processing_only: 2, all_failed: 4, near_limit: 19, answered: 6 }
  it.each(Object.entries(counts))('%s có %i tài liệu', async (s, n) => {
    setScenario(s as Scenario)
    resetMockState('lan@congty.vn')
    const list = await (await fetch(BASE + '/api/documents')).json()
    expect(list.count).toBe(n)
    expect(list.limit).toBe(20)
  })
  it('near_limit: tải lên lần 2 → DOCUMENT_LIMIT_REACHED', async () => {
    setScenario('near_limit')
    resetMockState('lan@congty.vn')
    const upload = () => {
      const fd = new FormData()
      fd.append('file', new File(['%PDF-1.4'], 'a.pdf', { type: 'application/pdf' }))
      return fetch(BASE + '/api/documents', { method: 'POST', headers: { 'X-Requested-With': 'fetch' }, body: fd })
    }
    expect((await upload()).status).toBe(201)
    const second = await upload()
    expect(second.status).toBe(409)
    expect((await second.json()).error.details.limit).toBe(20)
  })
})

describe('MSW: luồng hỏi đáp SSE', () => {
  async function ask(s: Scenario) {
    setScenario(s)
    resetMockState('lan@congty.vn')
    const conv = await (await post('/api/conversations')).json()
    const res = await post(`/api/conversations/${conv.id}/messages`, { question: 'Thời hạn thanh toán?' })
    return readSse(res)
  }

  const NO_READY: Scenario[] = ['empty_workspace', 'processing_only', 'all_failed']

  it.each(SCENARIOS.filter((s) => s !== 'interrupted' && !NO_READY.includes(s)))('%s: meta → status → … kết thúc bằng đúng một done/error', async (s) => {
    const { events, broken } = await ask(s)
    expect(broken).toBe(false)
    const names = events.map((e) => e.event)
    expect(names.slice(0, 2)).toEqual(['meta', 'status'])
    const terminal = names.filter((n) => n === 'done' || n === 'error')
    expect(terminal).toHaveLength(1)
    expect(names[names.length - 1]).toBe(terminal[0])
    // delta trước citation, citation trước done
    const lastDelta = names.lastIndexOf('delta')
    const firstCitation = names.indexOf('citation')
    if (firstCitation >= 0) expect(firstCitation).toBeGreaterThan(lastDelta)
  })

  it('answered: ghép delta thành câu trả lời có [1][2] và 2 trích dẫn', async () => {
    const { events } = await ask('answered')
    const text = events.filter((e) => e.event === 'delta').map((e) => JSON.parse(e.data).text).join('')
    expect(text).toContain('[1]')
    expect(text).toContain('[2]')
    expect(events.filter((e) => e.event === 'citation')).toHaveLength(2)
    expect(JSON.parse(events.at(-1)!.data).answer_type).toBe('answered')
  })

  it('timeout / llm_error kết thúc bằng error đúng mã', async () => {
    expect(JSON.parse((await ask('timeout')).events.at(-1)!.data).code).toBe('timeout')
    expect(JSON.parse((await ask('llm_error')).events.at(-1)!.data).code).toBe('llm_error')
  })

  it('interrupted: luồng đứt giữa chừng, không có done/error', async () => {
    const { events, broken } = await ask('interrupted')
    expect(broken).toBe(true)
    expect(events.some((e) => e.event === 'done' || e.event === 'error')).toBe(false)
  })

  it.each(NO_READY)('%s: hỏi khi chưa có tài liệu sẵn sàng → NO_READY_DOCUMENTS', async (s) => {
    setScenario(s)
    resetMockState('lan@congty.vn')
    const conv = await (await post('/api/conversations')).json()
    const r = await post(`/api/conversations/${conv.id}/messages`, { question: 'x' })
    expect(r.status).toBe(409)
    expect((await r.json()).error.code).toBe('NO_READY_DOCUMENTS')
  })

  it('citation detail theo trích dẫn đã trả', async () => {
    setScenario('answered')
    resetMockState('lan@congty.vn')
    const conv = await (await post('/api/conversations')).json()
    const { events } = await readSse(await post(`/api/conversations/${conv.id}/messages`, { question: 'q' }))
    const messageId = JSON.parse(events[0].data).message_id
    const detail = await (await fetch(`${BASE}/api/citations/${messageId}/1`)).json()
    expect(detail.page).toBe(12)
    expect(detail.highlight.snippet).toContain('30 (ba mươi) ngày')
    expect(mockState.messages.size).toBe(1)
  })
})

describe('SseParser', () => {
  it('ghép mẩu bị cắt giữa dòng, bỏ chú thích, đọc id', () => {
    const p = new SseParser()
    expect(p.push(': ping\n\nid: 7\nevent: del')).toEqual([])
    expect(p.push('ta\ndata: {"text":"a"}\n')).toEqual([])
    expect(p.push('\n')).toEqual([{ event: 'delta', data: '{"text":"a"}', id: '7' }])
  })
})
