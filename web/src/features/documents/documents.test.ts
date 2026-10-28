import { fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockState, setScenario, type Scenario } from '@/mocks'
import { server } from '@/mocks/server'
import type { Document, DocumentList } from '@/shared/api'
import { toasts } from '@/shared/ui/components/toast'
import { FakeEventSource } from '@/test/fakeEventSource'
import { renderApp } from '@/test/renderApp'
import DocumentRow from './DocumentRow.vue'
import DocumentsPanel from './DocumentsPanel.vue'
import EmptyWorkspaceState from './EmptyWorkspaceState.vue'
import { DOCUMENTS_KEY } from './api'
import { applyDocumentEvent } from './useDocuments'
import { useUploadQueue } from './upload/queue'
import type { Uploader } from './upload/xhrUpload'

const doc = (p: Partial<Document> = {}): Document => ({
  id: 'd1',
  name: 'HopDong_A.pdf',
  page_count: 48,
  status: 'ready',
  error_code: null,
  error_details: null,
  unreadable_pages: [],
  uploaded_by: { id: 'u1', display_name: 'Lan' },
  uploaded_at: new Date().toISOString(),
  can_delete: true,
  ...p,
})

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllGlobals()
})
afterAll(() => server.close())
beforeEach(() => {
  setScenario('answered')
  resetMockState('lan@congty.vn')
  toasts.splice(0)
  setActivePinia(createPinia())
})

describe('DocumentRow: mọi trạng thái có icon + chữ', () => {
  it.each([
    ['TOO_MANY_PAGES', { error_details: { page_count: 140 } }, 'File có 140 trang, vượt giới hạn 100 trang.'],
    ['PASSWORD_PROTECTED', {}, 'File được bảo vệ bằng mật khẩu. Hãy gỡ mật khẩu rồi tải lên lại.'],
    ['SCANNED_PDF', {}, 'Không đọc được văn bản trong file (có thể là bản scan). Hiện chưa hỗ trợ PDF dạng ảnh.'],
    ['CORRUPTED', {}, 'File bị lỗi, không mở được.'],
    ['SYSTEM_ERROR', {}, 'Xử lý thất bại do lỗi hệ thống.'],
  ])('AC-012.x: %s', (code, extra, text) => {
    render(DocumentRow, { props: { doc: doc({ status: 'failed', error_code: code as Document['error_code'], ...extra }) } })
    expect(screen.getByText('Lỗi')).toBeTruthy()
    expect(screen.getByText(text)).toBeTruthy()
    expect(!!screen.queryByRole('button', { name: /Thử lại/ })).toBe(code === 'SYSTEM_ERROR')
  })

  it('AC-012.8: sẵn sàng kèm "2 trang không đọc được văn bản: trang 7, 8"', () => {
    render(DocumentRow, { props: { doc: doc({ unreadable_pages: [7, 8] }) } })
    expect(screen.getByText('Sẵn sàng')).toBeTruthy()
    expect(screen.getByText('2 trang không đọc được văn bản: trang 7, 8')).toBeTruthy()
  })

  it('AC-011.1: tên rút gọn giữa (title = tên đầy đủ), số trang · người tải · thời điểm', () => {
    const long = 'BaoCao_Q3_2026_ban_cuoi_cung_da_duyet_v3.pdf'
    render(DocumentRow, { props: { doc: doc({ name: long }) } })
    expect(screen.getByTitle(long).textContent).toContain('…')
    expect(screen.getByText(/48 trang · Lan · \d\d:\d\d hôm nay/)).toBeTruthy()
  })

  it('đang xử lý / đang tải lên / gián đoạn', () => {
    const { unmount } = render(DocumentRow, { props: { doc: doc({ status: 'processing', page_count: null }) } })
    expect(screen.getByText('Đang xử lý')).toBeTruthy()
    unmount()
    const file = new File(['%PDF-'], 'a.pdf')
    const task = { localId: 'x', file, progress: 42, phase: 'uploading' as const, attempts: 0, batchSize: 1 }
    const r = render(DocumentRow, { props: { task } })
    expect(screen.getByText('Đang tải lên 42%')).toBeTruthy()
    expect(screen.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('42')
    r.unmount()
    render(DocumentRow, { props: { task: { ...task, phase: 'interrupted' as const } } })
    expect(screen.getByText('Gián đoạn')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeTruthy()
  })
})

function mountWithQuery(component: object, props: Record<string, unknown> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const pinia = createPinia()
  setActivePinia(pinia)
  const utils = render(component, { props, global: { plugins: [pinia, [VueQueryPlugin, { queryClient: qc }]] } })
  return { ...utils, qc }
}

describe('DocumentsPanel', () => {
  it('AC-011.3: bộ đếm x/20, đổi màu cảnh báo từ 18/20', async () => {
    setScenario('near_limit')
    resetMockState('lan@congty.vn')
    mountWithQuery(DocumentsPanel)
    const badge = await screen.findByText('19/20')
    expect(badge.className).toContain('warning')
  })

  it('AC-010.4: đủ 20 → vùng tải lên bị khóa và chọn thêm không gửi request', async () => {
    setScenario('near_limit')
    resetMockState('lan@congty.vn')
    const { qc } = mountWithQuery(DocumentsPanel)
    await screen.findByText('19/20')
    qc.setQueryData<DocumentList>(DOCUMENTS_KEY, (o) => ({ ...o!, items: [doc({ id: 'extra' }), ...o!.items], count: 20 }))
    expect(await screen.findByText('Đã đạt giới hạn 20 tài liệu')).toBeTruthy()
    const spy = vi.fn<Uploader>()
    useUploadQueue().configure({ uploader: spy })
    const input = screen.getByTestId('file-input') as HTMLInputElement
    await userEvent.setup().upload(input, new File(['%PDF-1.7'], 'thu21.pdf', { type: 'application/pdf' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Workspace đã đạt giới hạn 20 tài liệu')
    expect(spy).not.toHaveBeenCalled()
  })

  it('AC-010.5: 18 tài liệu + chọn 4 file → đúng 2 request, 2 thông báo limit_reached (tên file)', async () => {
    resetMockState('lan@congty.vn')
    const { qc } = mountWithQuery(DocumentsPanel)
    await screen.findByText('6/20')
    const many = Array.from({ length: 18 }, (_, i) => doc({ id: `x${i}`, name: `x${i}.pdf` }))
    qc.setQueryData<DocumentList>(DOCUMENTS_KEY, { items: many, count: 18, limit: 20 })
    const calls: string[] = []
    useUploadQueue().configure({ uploader: (f) => (calls.push(f.name), { done: new Promise(() => {}), abort: () => {} }) })
    const files = ['1.pdf', '2.pdf', '3.pdf', '4.pdf'].map((n) => new File(['%PDF-1.7'], n, { type: 'application/pdf' }))
    await userEvent.setup().upload(screen.getByTestId('file-input') as HTMLInputElement, files)
    await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(2))
    expect(calls).toEqual(['1.pdf', '2.pdf'])
    expect(screen.getAllByRole('alert').map((a) => a.textContent)).toEqual([expect.stringContaining('3.pdf'), expect.stringContaining('4.pdf')])
    expect(screen.getByText('20/20')).toBeTruthy()
  })

  it('201: tài liệu máy chủ trả về thay hàng "Đang tải lên", ở đầu danh sách, trạng thái Đang xử lý (không nhấp nháy)', async () => {
    resetMockState('lan@congty.vn')
    mountWithQuery(DocumentsPanel)
    await screen.findByText('6/20')
    let finish!: () => void
    useUploadQueue().configure({
      uploader: (file) => ({
        done: new Promise((resolve) => {
          finish = () => resolve({ status: 201, body: doc({ id: 'moi', name: file.name, status: 'processing', page_count: null }) })
        }),
        abort: () => {},
      }),
    })
    await userEvent.setup().upload(screen.getByTestId('file-input') as HTMLInputElement, new File(['%PDF-1.7'], 'Mới.pdf', { type: 'application/pdf' }))
    const list = await screen.findByRole('list', { name: 'Danh sách tài liệu' })
    await waitFor(() => expect(within(list).getAllByRole('listitem')[0]).toHaveTextContent(/Mới\.pdf.*Đang tải lên 0%/))
    finish()
    await waitFor(() => expect(within(list).getAllByRole('listitem')[0]).toHaveTextContent(/Mới\.pdf.*Đang xử lý/))
    expect(within(list).getAllByText('Mới.pdf')).toHaveLength(1)
    expect(screen.getByText('7/20')).toBeTruthy()
  })

  it('AC-012.7: SYSTEM_ERROR → Thử lại → hàng về Đang xử lý ngay', async () => {
    setScenario('all_failed')
    resetMockState('lan@congty.vn')
    mountWithQuery(DocumentsPanel)
    const btn = await screen.findByRole('button', { name: /Thử lại/ })
    await userEvent.setup().click(btn)
    const row = btn.closest('li') ?? document.querySelector('[data-doc-id$="000000000004"]')
    await waitFor(() => expect(row).toHaveTextContent('Đang xử lý'))
  })

  it('bỏ thông báo từ chối bằng nút ✕; axe-core 0 lỗi serious/critical', async () => {
    const { container } = mountWithQuery(DocumentsPanel)
    await screen.findByText('6/20')
    // applyAccept: false — giả lập người dùng chọn "Tất cả file" trong hộp thoại của hệ điều hành.
    await userEvent.setup({ applyAccept: false }).upload(screen.getByTestId('file-input') as HTMLInputElement, new File(['abc'], 'ghi-chu.txt'))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('ghi-chu.txt: Chỉ hỗ trợ file PDF.')
    const r = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(r.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id)).toEqual([])
    await userEvent.setup().click(within(alert).getByRole('button', { name: 'Bỏ thông báo' }))
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('applyDocumentEvent', () => {
  it('created thêm, status_changed thay, deleted gỡ, resync tải lại; toast chỉ cho tài liệu đầu tiên sẵn sàng', () => {
    const qc = new QueryClient()
    qc.setQueryData<DocumentList>(DOCUMENTS_KEY, { items: [], count: 0, limit: 20 })
    applyDocumentEvent(qc, 'document.created', doc({ id: 'a', status: 'processing' }))
    applyDocumentEvent(qc, 'document.created', doc({ id: 'b', status: 'processing' }))
    expect(qc.getQueryData<DocumentList>(DOCUMENTS_KEY)!.count).toBe(2)
    applyDocumentEvent(qc, 'document.status_changed', doc({ id: 'a', status: 'ready' }))
    applyDocumentEvent(qc, 'document.status_changed', doc({ id: 'b', status: 'ready' }))
    expect(toasts.map((x) => x.text)).toEqual(['Đã sẵn sàng. Hãy đặt câu hỏi.'])
    applyDocumentEvent(qc, 'document.deleted', { id: 'a' })
    expect(qc.getQueryData<DocumentList>(DOCUMENTS_KEY)!.items.map((d) => d.id)).toEqual(['b'])
    const spy = vi.spyOn(qc, 'invalidateQueries')
    applyDocumentEvent(qc, 'resync', {})
    expect(spy).toHaveBeenCalledWith({ queryKey: DOCUMENTS_KEY })
  })
})

describe('EmptyWorkspaceState: ba biến thể', () => {
  it.each<[Scenario, string]>([
    ['empty_workspace', 'Tải lên tài liệu PDF của dự án để bắt đầu'],
    ['processing_only', 'Đang xử lý tài liệu'],
    ['all_failed', 'Chưa có tài liệu nào dùng được'],
    ['answered', 'Bạn muốn tìm gì trong tài liệu?'],
  ])('%s', async (s, title) => {
    setScenario(s)
    resetMockState('lan@congty.vn')
    mountWithQuery(EmptyWorkspaceState)
    expect(await screen.findByText(title)).toBeTruthy()
  })
})

describe('tích hợp khung /c', () => {
  beforeEach(() => {
    FakeEventSource.reset()
    vi.stubGlobal('EventSource', FakeEventSource)
  })

  it('AC-011.2: sự kiện SSE cập nhật danh sách không cần tải lại; bộ đếm thanh trên mở/đóng bảng', async () => {
    window.innerWidth = 1440
    await renderApp('/c')
    const panel = await screen.findByRole('region', { name: 'Tài liệu' })
    await within(panel).findByText('6/20')
    FakeEventSource.last.open()
    FakeEventSource.last.emit('document.created', doc({ id: 'moi', name: 'NguoiKhac.pdf', status: 'processing' }))
    expect(await within(panel).findByText('NguoiKhac.pdf')).toBeTruthy()
    FakeEventSource.last.emit('document.status_changed', doc({ id: 'moi', name: 'NguoiKhac.pdf', status: 'ready', page_count: 3 }))
    await waitFor(() => expect(within(panel).getByText('NguoiKhac.pdf').closest('li')).toHaveTextContent('Sẵn sàng'))
    expect(within(panel).getByText('7/20')).toBeTruthy()

    await userEvent.setup().click(screen.getByTestId('doc-counter'))
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Tài liệu' })).toBeNull())
  })

  it('AC-003.1: session.revoked → /login?reason=revoked, xóa cache và bản nháp', async () => {
    sessionStorage.setItem('draft:u:c', 'nháp')
    const { router } = await renderApp('/c')
    await screen.findByLabelText('Câu hỏi')
    FakeEventSource.last.open()
    // Máy chủ thật đã xóa phiên trước khi phát session.revoked.
    resetMockState(null)
    FakeEventSource.last.emit('session.revoked', {})
    await waitFor(() => expect(router.currentRoute.value.query.reason).toBe('revoked'))
    expect(sessionStorage.getItem('draft:u:c')).toBeNull()
    expect(await screen.findByText(/vô hiệu hóa/)).toBeTruthy()
  })

  it('lớp phủ thả file chỉ hiện khi kéo FILE; thả ở bất kỳ đâu sẽ mở bảng Tài liệu và tải lên', async () => {
    window.innerWidth = 1100
    await renderApp('/c')
    await screen.findByLabelText('Câu hỏi')
    await fireEvent(window, Object.assign(new Event('dragenter'), { dataTransfer: { types: ['text/plain'] } }))
    expect(screen.queryByTestId('drop-overlay')).toBeNull()
    await fireEvent(window, Object.assign(new Event('dragenter'), { dataTransfer: { types: ['Files'] } }))
    expect(screen.getByTestId('drop-overlay')).toBeTruthy()
    const calls: string[] = []
    useUploadQueue().configure({ uploader: (f) => (calls.push(f.name), { done: new Promise(() => {}), abort: () => {} }) })
    const drop = Object.assign(new Event('drop', { cancelable: true }), {
      dataTransfer: { types: ['Files'], files: [new File(['%PDF-1.7'], 'tha.pdf', { type: 'application/pdf' })] },
    })
    window.dispatchEvent(drop)
    expect(drop.defaultPrevented).toBe(true)
    await waitFor(() => expect(calls).toEqual(['tha.pdf']))
    expect(screen.queryByTestId('drop-overlay')).toBeNull()
    expect(await screen.findByRole('region', { name: 'Tài liệu' })).toBeTruthy()
  })

  it('lỗi tải danh sách hiển thị trong vùng, có Thử lại', async () => {
    server.use(http.get('*/api/documents', () => HttpResponse.json({ error: { code: 'INTERNAL_ERROR', message_key: 'errors.generic', details: {} } }, { status: 500 })))
    window.innerWidth = 1440
    await renderApp('/c')
    const panel = await screen.findByRole('region', { name: 'Tài liệu' })
    // Lỗi máy chủ được thử lại 1 lần (≈ 1 s) trước khi hiển thị.
    expect(await within(panel).findByText(/Đã có lỗi xảy ra/, {}, { timeout: 5000 })).toBeTruthy()
  })
})
