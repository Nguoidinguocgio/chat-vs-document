import { fireEvent, screen, waitFor, within } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { mockState, resetMockState, setScenario, type Scenario } from '@/mocks'
import { server } from '@/mocks/server'
import { docId } from '@/mocks/data'
import { encodeFrame, type SseFrame } from '@/mocks/sse'
import { flush } from '@/shared/lib/analytics'
import { renderApp } from '@/test/renderApp'
import { XSS_PAYLOADS } from '@/shared/lib/markdown/markdown.test'
import { streamConfig } from './useAnswerStream'

const LONG = { timeout: 6000 }

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  streamConfig.silenceMs = 30_000
})
afterAll(() => server.close())

async function open(scenario: Scenario = 'answered', path = '/c') {
  setScenario(scenario)
  resetMockState('lan@congty.vn')
  sessionStorage.clear()
  const app = await renderApp(path)
  const box = (await screen.findByLabelText('Câu hỏi')) as HTMLTextAreaElement
  // chờ danh sách tài liệu tải xong (trạng thái ô nhập theo tài liệu)
  await waitFor(() => expect(screen.getByTestId('composer-status').dataset.state).not.toBe(undefined))
  return { ...app, box, user: userEvent.setup() }
}

const state = () => screen.getByTestId('composer-status').dataset.state
const card = () => screen.getAllByTestId('answer-card').at(-1)!

/** Phản hồi SSE tùy biến; `hang`: không đóng luồng (giả lập im lặng). */
function sseResponse(frames: SseFrame[], { hang = false, interval = 5 } = {}): Response {
  const enc = new TextEncoder()
  let timer: ReturnType<typeof setTimeout> | undefined
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      let i = 0
      const tick = () => {
        if (i >= frames.length) {
          if (!hang) c.close()
          return
        }
        c.enqueue(enc.encode(encodeFrame(frames[i++])))
        timer = setTimeout(tick, interval)
      }
      tick()
    },
    cancel() {
      clearTimeout(timer)
    },
  })
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

const META: SseFrame = { event: 'meta', data: { message_id: 'ffffffff-0000-4000-8000-0000000000aa', lang: 'vi' } }

function useStream(frames: SseFrame[], opts?: { hang?: boolean; interval?: number }) {
  server.use(
    http.post('*/api/conversations/:id/messages', () => sseResponse(frames, opts)),
  )
}

describe('ST-04 / AC-020.4: trạng thái ô nhập theo tài liệu', () => {
  it.each<[Scenario, string, RegExp]>([
    ['empty_workspace', 'no_documents', /Tải lên ít nhất một tài liệu PDF để bắt đầu hỏi/],
    ['processing_only', 'processing_only', /Đang xử lý \d+ tài liệu, bạn có thể hỏi khi/],
    ['all_failed', 'all_failed', /Chưa có tài liệu nào dùng được/],
  ])('%s → %s, ô nhập bị khóa, Disclaimer vẫn hiển thị', async (scenario, code, text) => {
    const { box } = await open(scenario)
    await waitFor(() => expect(state()).toBe(code))
    expect(box).toBeDisabled()
    expect(within(screen.getByTestId('composer-status')).getByText(text)).toBeTruthy()
    expect(screen.getByTestId('disclaimer')).toHaveTextContent('AI có thể sai')
    if (code === 'no_documents') expect(within(screen.getByTestId('composer-status')).getByRole('button', { name: /Tải lên/ })).toBeTruthy()
    if (code === 'all_failed') expect(within(screen.getByTestId('composer-status')).getByRole('button', { name: 'Mở danh sách tài liệu' })).toBeTruthy()
  })
})

describe('Composer', () => {
  beforeEach(() => {
    streamConfig.silenceMs = 30_000
  })

  it('2.000 ký tự gửi được; 2.001 bị chặn với bộ đếm đỏ; chỉ khoảng trắng → Gửi vô hiệu hóa', async () => {
    const { box } = await open()
    await waitFor(() => expect(state()).toBe('empty'))
    const send = screen.getByRole('button', { name: 'Gửi câu hỏi' })
    await fireEvent.update(box, '   \n  ')
    expect(state()).toBe('empty')
    expect(send).toBeDisabled()
    await fireEvent.update(box, 'a'.repeat(1799))
    expect(screen.queryByTestId('char-count')).toBeNull()
    await fireEvent.update(box, 'a'.repeat(2001))
    expect(state()).toBe('too_long')
    expect(send).toBeDisabled()
    expect(screen.getByTestId('char-count')).toHaveTextContent('2001/2000')
    expect(screen.getByTestId('char-count').className).toContain('text-danger')
    expect(screen.getByText('Câu hỏi tối đa 2.000 ký tự')).toBeTruthy()
    await fireEvent.update(box, 'a'.repeat(2000))
    expect(state()).toBe('ready')
    expect(send).not.toBeDisabled()
  })

  it('Enter gửi, Shift+Enter xuống dòng, KHÔNG gửi khi đang soạn dấu bằng IME', async () => {
    useStream([META, { event: 'done', data: { answer_type: 'not_found', missing: null } }])
    const { box, user } = await open()
    await user.type(box, 'Dòng một{Shift>}{Enter}{/Shift}dòng hai')
    expect(box.value).toBe('Dòng một\ndòng hai')
    expect(screen.queryByTestId('answer-card')).toBeNull()

    // IME: compositionstart … Enter (isComposing / keyCode 229) … compositionend
    await fireEvent.compositionStart(box)
    await fireEvent.keyDown(box, { key: 'Enter', isComposing: true })
    await fireEvent.keyDown(box, { key: 'Enter', keyCode: 229 })
    expect(screen.queryByTestId('answer-card')).toBeNull()
    await fireEvent.compositionEnd(box)
    await fireEvent.keyDown(box, { key: 'Enter', keyCode: 229 })
    expect(screen.queryByTestId('answer-card')).toBeNull()

    await fireEvent.keyDown(box, { key: 'Enter' })
    expect(screen.getByTestId('answer-card')).toBeTruthy()
    expect(box.value).toBe('')
  })

  it('bấm câu hỏi gợi ý chỉ điền vào ô nhập, không gửi', async () => {
    const { box, user } = await open()
    expect(await screen.findByText('Bạn muốn tìm gì trong tài liệu?')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Doanh thu quý 3 là bao nhiêu?' }))
    expect(box.value).toBe('Doanh thu quý 3 là bao nhiêu?')
    expect(document.activeElement).toBe(box)
    expect(screen.queryByTestId('answer-card')).toBeNull()
  })

  it('phím tắt: "/" focus ô nhập; Ctrl+Shift+O → hội thoại mới', async () => {
    const { box, router } = await open()
    ;(document.activeElement as HTMLElement | null)?.blur()
    await fireEvent.keyDown(document.body, { key: '/' })
    expect(document.activeElement).toBe(box)
    await router.push('/c/khong-ton-tai')
    await fireEvent.keyDown(document.body, { key: 'O', ctrlKey: true, shiftKey: true })
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
  })
})

describe('Hỏi đáp có trích dẫn (T16)', () => {
  it('AC-020.1 / AC-022.1 / AC-021.2: "Đang tìm…" ngay khi gửi → stream → chip [1][2] + NGUỒN; URL /c/:id; danh sách hội thoại; EV-021', async () => {
    const sent: { name: string; props: Record<string, unknown> }[] = []
    server.use(
      http.post('*/api/analytics/events', async ({ request }) => {
        sent.push(...((await request.json()) as { events: typeof sent }).events)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { box, user, router, container } = await open()
    await user.type(box, 'Thời hạn thanh toán?')
    await user.keyboard('{Enter}')

    // thẻ trả lời xuất hiện ngay (không chờ máy chủ)
    expect(card().dataset.state).toBe('searching')
    expect(within(card()).getByText('Đang tìm trong tài liệu…')).toBeTruthy()
    expect(screen.getByTestId('chat-announcer')).toHaveTextContent('Đang tạo câu trả lời')
    expect(screen.getByRole('button', { name: 'Dừng tạo câu trả lời' })).toBeTruthy()

    await waitFor(() => expect(card().dataset.state).toBe('streaming'), LONG)
    expect(card()).toHaveAttribute('aria-busy', 'true')
    expect(card().querySelector('.md-cursor')).not.toBeNull()
    // AC-020.3: đang trả lời → Enter không gửi câu mới
    await user.type(box, 'câu khác{Enter}')
    expect(screen.getAllByTestId('answer-card')).toHaveLength(1)

    await waitFor(() => expect(card().dataset.state).toBe('answered'), LONG)
    expect(card()).toHaveAttribute('aria-busy', 'false')
    expect(card()).toHaveAttribute('lang', 'vi')
    expect(card().querySelector('.md-cursor')).toBeNull()
    expect(within(card()).getByRole('button', { name: 'Trích dẫn 1: HopDong_A.pdf, trang 12' })).toBeTruthy()
    expect(within(card()).getByRole('button', { name: 'Trích dẫn 2: DeXuat_B.pdf, trang 4' })).toBeTruthy()
    const sources = within(screen.getByTestId('source-list'))
    expect(sources.getByText('Nguồn')).toBeTruthy()
    expect(sources.getByRole('button', { name: '[1] HopDong_A.pdf, trang 12' })).toBeTruthy()
    expect(sources.getByRole('button', { name: '[2] DeXuat_B.pdf, trang 4' })).toBeTruthy()
    expect(screen.getByTestId('chat-announcer')).toHaveTextContent('Câu trả lời đã xong, có 2 trích dẫn')
    expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).toBeTruthy()
    await waitFor(() => expect(document.activeElement).toBe(box))

    // URL /c → /c/:id, hội thoại xuất hiện ở thanh trái với aria-current
    const id = mockState.conversations[0].id
    expect(router.currentRoute.value.path).toBe(`/c/${id}`)
    const link = await screen.findByRole('link', { name: 'Thời hạn thanh toán?' })
    expect(link).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText('Hôm nay')).toBeTruthy()

    // EV-021 gửi đúng một lần, không có nội dung
    await flush()
    const ev = sent.filter((e) => e.name === 'chat_answer_first_token')
    expect(ev).toHaveLength(1)
    expect(Object.keys(ev[0].props).sort()).toEqual(['question_id', 'ttft_ms'])
    expect(ev[0].props.ttft_ms).toBeGreaterThanOrEqual(0)

    const result = await axe.run(container)
    expect(result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')).toEqual([])
  })

  it('AC-023.1/023.2: not_found → thông điệp + gợi ý, KHÔNG có danh sách nguồn', async () => {
    const { box, user } = await open('not_found')
    await user.type(box, 'Luật áp dụng?{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('notfound'), LONG)
    expect(within(card()).getByText('Không tìm thấy thông tin này trong các tài liệu hiện có.')).toBeTruthy()
    expect(within(card()).getByText(/kiểm tra tài liệu liên quan đã được tải lên/)).toBeTruthy()
    expect(screen.queryByTestId('source-list')).toBeNull()
    expect(screen.getByTestId('chat-announcer')).toHaveTextContent('Không tìm thấy thông tin')
  })

  it('AC-023.3: partial → văn bản có trích dẫn + khối "Không tìm thấy: …"', async () => {
    const { box, user } = await open('partial')
    await user.type(box, 'Doanh thu quý 3?{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('partial'), LONG)
    expect(within(screen.getByTestId('missing')).getByText(/Biên lợi nhuận theo từng mảng/)).toBeTruthy()
    expect(screen.getByTestId('source-list')).toBeTruthy()
  })

  it('luồng đóng mà không có done → interrupted (giữ phần đã nhận, có Thử lại)', async () => {
    const { box, user } = await open('interrupted')
    await user.type(box, 'Thời hạn?{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('interrupted'), LONG)
    expect(within(card()).getByText('Kết nối bị gián đoạn — câu trả lời chưa hoàn chỉnh.')).toBeTruthy()
    expect(card().textContent).toContain('Hai tài liệu')
    expect(within(card()).getByRole('button', { name: 'Thử lại' })).toBeTruthy()
    expect(screen.queryByTestId('source-list')).toBeNull()
  })

  it('error llm_error → thông điệp dịch vụ AI; error timeout → thông điệp chậm', async () => {
    const { box, user } = await open('llm_error')
    await user.type(box, 'q1{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('failed'), LONG)
    expect(within(card()).getByText(/Dịch vụ AI tạm thời không khả dụng/)).toBeTruthy()
    setScenario('timeout')
    await user.type(box, 'q2{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('timeout'), LONG)
    expect(within(card()).getByText('Hệ thống phản hồi chậm hơn bình thường. Thử lại?')).toBeTruthy()
  })

  it('im lặng quá hạn (không sự kiện, không ping) → timeout và hủy luồng', async () => {
    streamConfig.silenceMs = 300
    useStream([META, { event: 'delta', data: { text: 'Bắt đầu ' } }], { hang: true })
    const { box, user } = await open()
    await user.type(box, 'q{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('timeout'), LONG)
    expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).toBeTruthy()
  })

  it('NFR-S05: payload XSS qua luồng SSE giả → không script, không img/a/iframe, không on*/style', async () => {
    const frames: SseFrame[] = [META, ...XSS_PAYLOADS.slice(0, 12).map((p) => ({ event: 'delta', data: { text: p + '\n\n' } }))]
    frames.push({ event: 'citation', data: { index: 1, document_id: docId(1), document_name: '<img src=x onerror=alert(1)>.pdf', page: 1, document_deleted: false } })
    frames.push({ event: 'done', data: { answer_type: 'answered', missing: null } })
    useStream(frames)
    const { box, user } = await open()
    await user.type(box, 'q{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('answered'), LONG)
    const root = card()
    expect(root.querySelectorAll('img, a, iframe, script, svg, style, details')).toHaveLength(0)
    for (const el of root.querySelectorAll('*')) {
      for (const attr of el.getAttributeNames()) expect(attr).not.toMatch(/^(on|style|href|src)/)
    }
    // tên tài liệu độc hại chỉ là chữ trong aria-label
    expect(within(root).getByRole('button', { name: 'Trích dẫn 1: <img src=x onerror=alert(1)>.pdf, trang 1' })).toBeTruthy()
  })

  it('409 NO_READY_DOCUMENTS / 422 QUESTION_INVALID trước luồng: bỏ thẻ tạm, không treo ô nhập', async () => {
    server.use(http.post('*/api/conversations/:id/messages', () => HttpResponse.json({ error: { code: 'QUESTION_INVALID', message_key: 'chat.question_invalid', details: {} } }, { status: 422 })))
    const { box, user } = await open()
    await user.type(box, 'q{Enter}')
    await waitFor(() => expect(screen.queryByTestId('answer-card')).toBeNull())
    expect(screen.getByText('Câu hỏi tối đa 2.000 ký tự')).toBeTruthy()
    server.use(http.post('*/api/conversations/:id/messages', () => HttpResponse.json({ error: { code: 'NO_READY_DOCUMENTS', message_key: 'chat.empty_workspace', details: {} } }, { status: 409 })))
    mockState.documents = []
    await user.type(box, 'q{Enter}')
    await waitFor(() => expect(state()).toBe('no_documents'))
    expect(screen.queryByTestId('answer-card')).toBeNull()
  })

  it('rời hội thoại khi đang trả lời → hủy luồng (không rò rỉ), hội thoại mới trống', async () => {
    useStream([META, ...Array.from({ length: 200 }, () => ({ event: 'delta', data: { text: 'chữ ' } }))], { interval: 30 })
    const { box, user, router } = await open()
    await user.type(box, 'q{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('streaming'), LONG)
    await user.click(screen.getByTestId('new-conversation'))
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
    expect(screen.queryByTestId('answer-card')).toBeNull()
    expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).toBeTruthy()
  })
})

describe('/c/:id', () => {
  it('tải lại toàn bộ tin nhắn đã lưu (kể cả trích dẫn)', async () => {
    const first = await open()
    await first.user.type(first.box, 'Thời hạn thanh toán?{Enter}')
    await waitFor(() => expect(card().dataset.state).toBe('answered'), LONG)
    const id = mockState.conversations[0].id
    first.unmount()
    const app = await renderApp(`/c/${id}`)
    await waitFor(() => expect(card().dataset.state).toBe('answered'))
    expect(screen.getByText('Thời hạn thanh toán?', { selector: '[role=group]' })).toBeTruthy()
    expect(within(card()).getByRole('button', { name: 'Trích dẫn 2: DeXuat_B.pdf, trang 4' })).toBeTruthy()
    app.unmount()
  })

  it('không tồn tại / của người khác → "Không tìm thấy hội thoại"', async () => {
    setScenario('answered')
    resetMockState('lan@congty.vn')
    await renderApp('/c/00000000-0000-4000-8000-000000000999')
    expect(await screen.findByTestId('conversation-not-found')).toHaveTextContent('Không tìm thấy hội thoại')
  })
})
