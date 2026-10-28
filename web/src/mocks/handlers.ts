import { delay, http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema'
import { CITATION_TEXT, DISABLED_EMAIL, LOCKED_EMAIL, MOCK_PASSWORD, MOCK_USERS, newDocument, seedDocuments } from './data'
import { getScenario } from './scenario'
import { answerFrames, encodeFrame, sseStream, type SseFrame } from './sse'

type CurrentUser = components['schemas']['CurrentUser']
type Document = components['schemas']['Document']
type Conversation = components['schemas']['Conversation']
type Message = components['schemas']['Message']

const MAX_DOCS = 20
const MAX_BYTES = 10 * 1024 * 1024

/** Trạng thái giả trong bộ nhớ (reset khi tải lại trang / giữa các test). */
export const mockState = {
  user: null as CurrentUser | null,
  documents: [] as Document[],
  conversations: [] as Conversation[],
  messages: new Map<string, Message[]>(),
  eventSeq: 0,
  subscribers: new Set<ReadableStreamDefaultController<Uint8Array>>(),
}

export function resetMockState(loggedInAs: keyof typeof MOCK_USERS | null = null): void {
  mockState.user = loggedInAs ? { ...MOCK_USERS[loggedInAs] } : null
  mockState.documents = seedDocuments(getScenario())
  mockState.conversations = []
  mockState.messages.clear()
}
resetMockState()

const err = (status: number, code: string, messageKey: string, details: Record<string, unknown> = {}) =>
  HttpResponse.json({ error: { code, message_key: messageKey, details } }, { status })

const PASSWORD_FREE = new Set(['/api/me', '/api/auth/logout', '/api/auth/change-password'])

/** Kiểm tra đăng nhập + CSRF + bắt buộc đổi mật khẩu như backend; trả lỗi hoặc null. */
function guard(request: Request): Response | null {
  const url = new URL(request.url)
  if (request.method !== 'GET' && request.headers.get('X-Requested-With') !== 'fetch') {
    return err(403, 'FORBIDDEN', 'errors.forbidden')
  }
  if (!mockState.user) return err(401, 'UNAUTHENTICATED', 'errors.unauthenticated')
  if (mockState.user.must_change_password && !PASSWORD_FREE.has(url.pathname)) {
    return err(403, 'PASSWORD_CHANGE_REQUIRED', 'errors.password_change_required')
  }
  return null
}

function viewFor(d: Document): Document {
  const u = mockState.user
  return { ...d, can_delete: !!u && (u.role === 'admin' || u.id === d.uploaded_by.id) }
}

function broadcast(event: string, data: unknown): void {
  const frame = new TextEncoder().encode(encodeFrame({ id: String(++mockState.eventSeq), event, data }))
  for (const c of mockState.subscribers) {
    try {
      c.enqueue(frame)
    } catch {
      mockState.subscribers.delete(c)
    }
  }
}

/** Tài liệu "đang xử lý" chuyển sang sẵn sàng sau một lúc (giả lập pipeline T08). */
/** Tài liệu đã bấm "Thử lại": lần xử lý sau thành công. */
const retried = new Set<string>()

function finishLater(d: Document, ms = 1500): void {
  setTimeout(() => {
    const current = mockState.documents.find((x) => x.id === d.id)
    if (!current || current.status !== 'processing') return
    // Tên file điều khiển kết quả xử lý giả (giống gợi ý trong prototype SCR-03).
    const n = retried.has(current.id) ? '' : current.name.toLowerCase()
    if (n.includes('scan')) Object.assign(current, { status: 'failed', error_code: 'SCANNED_PDF', page_count: 8 })
    else if (n.includes('error')) Object.assign(current, { status: 'failed', error_code: 'SYSTEM_ERROR' })
    else if (n.includes('pass')) Object.assign(current, { status: 'failed', error_code: 'PASSWORD_PROTECTED' })
    else if (n.includes('big')) Object.assign(current, { status: 'failed', error_code: 'TOO_MANY_PAGES', page_count: 140, error_details: { page_count: 140 } })
    else if (n.includes('partial')) Object.assign(current, { status: 'ready', page_count: 12, unreadable_pages: [3, 4] })
    else Object.assign(current, { status: 'ready', page_count: current.page_count ?? 12 })
    broadcast('document.status_changed', viewFor(current))
  }, ms)
}

const sse = (body: ReadableStream<Uint8Array>) =>
  new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } })

/** PDF một trang tối thiểu hợp lệ (offset xref tính lúc chạy) cho bảng Xem nguồn. */
function minimalPdf(text: string): string {
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${text.length + 40} >>\nstream\nBT /F1 14 Tf 60 780 Td (${text}) Tj ET\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]
  let out = '%PDF-1.4\n'
  const offsets: number[] = []
  objs.forEach((o, i) => {
    offsets.push(out.length)
    out += `${i + 1} 0 obj\n${o}\nendobj\n`
  })
  const xref = out.length
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`
  offsets.forEach((o) => (out += `${String(o).padStart(10, '0')} 00000 n \n`))
  out += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return out
}

function answerResponse(messageId: string, question: string, conversationId: string): Response {
  const scenario = getScenario()
  const { frames, endAbruptly } = answerFrames(scenario, messageId)
  const list = mockState.messages.get(conversationId) ?? []
  list.push({
    id: messageId,
    question,
    answer: frames.filter((f) => f.event === 'delta').map((f) => (f.data as { text: string }).text).join(''),
    lang: 'vi',
    status: 'completed',
    answer_type: scenario === 'not_found' ? 'not_found' : scenario === 'partial' ? 'partial' : 'answered',
    missing: null,
    error_code: null,
    citations: frames.filter((f) => f.event === 'citation').map((f) => f.data as components['schemas']['Citation']),
    created_at: new Date().toISOString(),
  })
  mockState.messages.set(conversationId, list)
  return sse(sseStream(frames, undefined, endAbruptly))
}

let uuidSeq = 0
const mockUuid = () => `ffffffff-0000-4000-8000-${String(++uuidSeq).padStart(12, '0')}`

export const handlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    if (request.headers.get('X-Requested-With') !== 'fetch') return err(403, 'FORBIDDEN', 'errors.forbidden')
    const body = (await request.json()) as { email?: string; password?: string }
    const email = (body.email ?? '').trim().toLowerCase()
    await delay(150)
    if (email === LOCKED_EMAIL) return err(423, 'ACCOUNT_LOCKED', 'auth.locked', { retry_after_s: 840 })
    if (body.password !== MOCK_PASSWORD) return err(401, 'INVALID_CREDENTIALS', 'auth.invalid_credentials')
    if (email === DISABLED_EMAIL) return err(403, 'ACCOUNT_DISABLED', 'auth.disabled')
    const user = MOCK_USERS[email]
    if (!user) return err(401, 'INVALID_CREDENTIALS', 'auth.invalid_credentials')
    mockState.user = { ...user }
    return HttpResponse.json(mockState.user)
  }),

  http.post('*/api/auth/logout', () => {
    mockState.user = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/me', ({ request }) => guard(request) ?? HttpResponse.json(mockState.user)),

  http.post('*/api/auth/change-password', async ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    const body = (await request.json()) as { current_password?: string | null; new_password?: string }
    const pw = body.new_password ?? ''
    if (!mockState.user!.must_change_password && body.current_password !== MOCK_PASSWORD) {
      return err(401, 'INVALID_CREDENTIALS', 'auth.invalid_credentials')
    }
    if (pw.length < 10 || !/\p{L}/u.test(pw) || !/\d/.test(pw) || pw === MOCK_PASSWORD) {
      return err(422, 'VALIDATION_ERROR', 'auth.password_policy')
    }
    mockState.user!.must_change_password = false
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/documents', ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    const items = mockState.documents.map(viewFor)
    return HttpResponse.json({ items, count: items.length, limit: MAX_DOCS })
  }),

  http.post('*/api/documents', async ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    const form = await request.formData()
    const file = form.get('file')
    // Kiểm theo hình dạng: File của jsdom và của Node là hai lớp khác nhau trong test.
    if (!file || typeof file === 'string') return err(422, 'VALIDATION_ERROR', 'errors.validation', { reason: 'file_missing' })
    if (!file.name.toLowerCase().endsWith('.pdf')) return err(400, 'NOT_PDF', 'documents.not_pdf')
    if (file.size > MAX_BYTES) return err(413, 'FILE_TOO_LARGE', 'documents.too_large')
    if (mockState.documents.length >= MAX_DOCS) return err(409, 'DOCUMENT_LIMIT_REACHED', 'documents.limit_reached', { limit: MAX_DOCS })
    if (file.name.toLowerCase().includes('netfail')) return HttpResponse.error()
    if (file.name.toLowerCase().includes('slow')) await delay(4000)
    const d = newDocument(file.name, mockState.user!)
    mockState.documents.unshift(d)
    broadcast('document.created', viewFor(d))
    finishLater(d)
    return HttpResponse.json(viewFor(d), { status: 201 })
  }),

  http.post('*/api/documents/:id/retry', ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    const d = mockState.documents.find((x) => x.id === params.id)
    if (!d) return err(404, 'NOT_FOUND', 'errors.not_found')
    if (d.status !== 'failed' || d.error_code !== 'SYSTEM_ERROR') return err(409, 'NOT_RETRYABLE', 'documents.not_retryable')
    Object.assign(d, { status: 'processing', error_code: null })
    retried.add(d.id)
    broadcast('document.status_changed', viewFor(d))
    finishLater(d)
    return HttpResponse.json(viewFor(d), { status: 202 })
  }),

  http.get('*/api/documents/:id/file', ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    const d = mockState.documents.find((x) => x.id === params.id)
    if (!d) return err(404, 'NOT_FOUND', 'errors.not_found')
    return new HttpResponse(minimalPdf(`Mock ${d.name}`), { headers: { 'Content-Type': 'application/pdf' } })
  }),

  http.get('*/api/events', ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    let ctrl: ReadableStreamDefaultController<Uint8Array>
    let hb: ReturnType<typeof setInterval>
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        ctrl = c
        mockState.subscribers.add(c)
        c.enqueue(new TextEncoder().encode(': connected\n\n'))
        hb = setInterval(() => c.enqueue(new TextEncoder().encode(encodeFrame({ event: 'heartbeat', data: {} }))), 20_000)
        mockState.documents.filter((d) => d.status === 'processing' && getScenario() !== 'processing_only').forEach((d) => finishLater(d, 2500))
      },
      cancel() {
        clearInterval(hb)
        mockState.subscribers.delete(ctrl)
      },
    })
    return sse(body)
  }),

  http.post('*/api/analytics/events', ({ request }) => guard(request) ?? new HttpResponse(null, { status: 204 })),

  http.get('*/api/conversations', ({ request }) => guard(request) ?? HttpResponse.json(mockState.conversations)),

  http.post('*/api/conversations', ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    const now = new Date().toISOString()
    const c: Conversation = { id: mockUuid(), title: 'Hội thoại mới', created_at: now, last_activity_at: now }
    mockState.conversations.unshift(c)
    return HttpResponse.json(c, { status: 201 })
  }),

  http.get('*/api/conversations/:id', ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    const c = mockState.conversations.find((x) => x.id === params.id)
    if (!c) return err(404, 'NOT_FOUND', 'errors.not_found')
    return HttpResponse.json({ conversation: c, messages: mockState.messages.get(c.id) ?? [] })
  }),

  http.post('*/api/conversations/:id/messages', async ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    const body = (await request.json()) as { question?: string }
    const q = (body.question ?? '').trim()
    if (!q || q.length > 2000) return err(422, 'QUESTION_INVALID', 'chat.question_invalid')
    if (!mockState.documents.some((d) => d.status === 'ready')) return err(409, 'NO_READY_DOCUMENTS', 'chat.empty_workspace')
    const c = mockState.conversations.find((x) => x.id === params.id)
    if (!c) return err(404, 'NOT_FOUND', 'errors.not_found')
    if (c.title === 'Hội thoại mới') c.title = q.slice(0, 60)
    c.last_activity_at = new Date().toISOString()
    return answerResponse(mockUuid(), q, c.id)
  }),

  http.post('*/api/messages/:id/stop', ({ request }) => guard(request) ?? new HttpResponse(null, { status: 204 })),

  http.post('*/api/messages/:id/regenerate', ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    for (const [conversationId, list] of mockState.messages) {
      const m = list.find((x) => x.id === params.id)
      if (m) return answerResponse(mockUuid(), m.question, conversationId)
    }
    return err(409, 'NOT_REGENERABLE', 'chat.not_regenerable')
  }),

  http.get('*/api/citations/:messageId/:index', ({ request, params }) => {
    const denied = guard(request)
    if (denied) return denied
    for (const list of mockState.messages.values()) {
      const m = list.find((x) => x.id === params.messageId)
      const cite = m?.citations.find((c) => c.index === Number(params.index))
      if (cite) {
        if (cite.document_deleted) return err(410, 'DOCUMENT_DELETED', 'source.deleted')
        const t = CITATION_TEXT[`${cite.document_id}:${cite.page}`]
        return HttpResponse.json({
          document_id: cite.document_id,
          document_name: cite.document_name,
          page: cite.page,
          page_count: t?.page_count ?? cite.page,
          highlight: t ? { snippet: t.snippet, page_text_start: 0, page_text_end: t.snippet.length } : null,
        })
      }
    }
    return err(404, 'NOT_FOUND', 'errors.not_found')
  }),
]

export type { SseFrame }
