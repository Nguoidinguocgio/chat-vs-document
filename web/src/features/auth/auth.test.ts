import { screen, waitFor } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { http, HttpResponse } from 'msw'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { DISABLED_EMAIL, LOCKED_EMAIL, MOCK_PASSWORD, resetMockState, setScenario } from '@/mocks'
import { server } from '@/mocks/server'
import { request } from '@/shared/api'
import { DRAFT_DEBOUNCE_MS } from '@/shared/lib/draft'
import { renderApp } from '@/test/renderApp'
import { safeNext } from './api'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
beforeEach(() => {
  setScenario('answered')
  resetMockState()
  sessionStorage.clear()
})

async function login(email: string, password = MOCK_PASSWORD) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Mật khẩu'), password)
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
  return user
}

async function noSeriousA11yViolations(container: Element) {
  const r = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(r.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id)).toEqual([])
}

describe('AC-001.6: chưa đăng nhập', () => {
  it('vào thẳng /c → /login?next=/c, không hiển thị dữ liệu ứng dụng', async () => {
    const { router } = await renderApp('/c')
    await waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
    expect(router.currentRoute.value.query.next).toBe('/c')
    expect(screen.queryByLabelText('Câu hỏi')).toBeNull()
    expect(screen.queryByText('Tài liệu')).toBeNull()
  })
})

describe('AC-001.1 / 001.2: đăng nhập', () => {
  it('đúng → vào /c và thấy tên người dùng', async () => {
    const { router } = await renderApp('/login')
    await login('lan@congty.vn')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
    expect(await screen.findByRole('button', { name: 'Tài khoản Lan' })).toBeTruthy()
  })

  it('sai mật khẩu và sai email cho cùng một thông báo', async () => {
    await renderApp('/login')
    const user = await login('lan@congty.vn', 'sai-mat-khau')
    const a = (await screen.findByTestId('login-notice')).textContent
    await user.clear(screen.getByLabelText('Email'))
    await user.type(screen.getByLabelText('Email'), 'khong-co@congty.vn')
    await user.type(screen.getByLabelText('Mật khẩu'), MOCK_PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    await waitFor(() => expect(screen.getByTestId('login-notice').textContent).toBe(a))
    expect(a).toContain('Email hoặc mật khẩu không đúng.')
  })

  it('kiểm tra tại chỗ: email rỗng/sai định dạng, mật khẩu rỗng', async () => {
    await renderApp('/login')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Đăng nhập' }))
    expect(screen.getByText('Nhập email.')).toBeTruthy()
    await user.type(screen.getByLabelText('Email'), 'khong-hop-le')
    expect(screen.getByText('Email không đúng định dạng.')).toBeTruthy()
    expect(screen.getByText('Nhập mật khẩu.')).toBeTruthy()
    expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBe('true')
  })

  it('nút hiện/ẩn mật khẩu', async () => {
    await renderApp('/login')
    const user = userEvent.setup()
    const pw = await screen.findByLabelText('Mật khẩu')
    expect(pw.getAttribute('type')).toBe('password')
    await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(pw.getAttribute('type')).toBe('text')
  })
})

describe('AC-001.3 và tài khoản vô hiệu hóa', () => {
  it('423 hiển thị auth.locked với số phút từ retry_after_s', async () => {
    await renderApp('/login')
    await login(LOCKED_EMAIL)
    expect((await screen.findByTestId('login-notice')).textContent).toContain('Thử lại sau 14 phút')
  })
  it('ACCOUNT_DISABLED hiển thị auth.disabled', async () => {
    await renderApp('/login')
    await login(DISABLED_EMAIL)
    expect((await screen.findByTestId('login-notice')).textContent).toContain('Tài khoản đã bị vô hiệu hóa, liên hệ Admin.')
  })
  it('reason=revoked hiển thị auth.disabled; reason=expired hiển thị auth.session_expired', async () => {
    await renderApp('/login?reason=revoked')
    expect((await screen.findByTestId('login-notice')).textContent).toContain('vô hiệu hóa')
  })
})

describe('chuyển hướng next', () => {
  it('đăng nhập xong về next nội bộ', async () => {
    const { router } = await renderApp('/login?next=/c/abc')
    await login('lan@congty.vn')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c/abc'))
  })
  it.each(['https://x.com', '//x.com', '/\\x.com', 'javascript:alert(1)', '/login'])('bỏ qua next ngoài/không an toàn: %s', (next) => {
    expect(safeNext(next)).toBe('/c')
  })
  it('đã đăng nhập vào /login → /c', async () => {
    resetMockState('lan@congty.vn')
    const { router } = await renderApp('/login')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
  })
})

describe('AC-001.4: mật khẩu tạm', () => {
  it('bị giữ ở /change-password?mode=required cho tới khi đổi đạt chính sách', async () => {
    const { router } = await renderApp('/login')
    const user = await login('moi@congty.vn')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/change-password'))
    expect(router.currentRoute.value.query.mode).toBe('required')
    expect(screen.getByTestId('required-notice')).toBeTruthy()
    expect(screen.queryByLabelText('Mật khẩu hiện tại')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Hủy' })).toBeNull()

    await router.push('/c')
    expect(router.currentRoute.value.path).toBe('/change-password')

    const next = screen.getByLabelText('Mật khẩu mới')
    await user.type(next, 'abc')
    expect(document.querySelector('[data-rule="length"]')!.getAttribute('data-ok')).toBe('false')
    await user.type(next, 'defgh12')
    expect(document.querySelector('[data-rule="length"]')!.getAttribute('data-ok')).toBe('true')
    expect(document.querySelector('[data-rule="digit"]')!.getAttribute('data-ok')).toBe('true')
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'khac')
    await user.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }))
    expect(screen.getByText('Mật khẩu nhập lại không khớp.')).toBeTruthy()
    await user.clear(screen.getByLabelText('Nhập lại mật khẩu mới'))
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'abcdefgh12')
    await user.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }))
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
  })

  it('chế độ đổi thường cần mật khẩu hiện tại và có nút Hủy', async () => {
    resetMockState('lan@congty.vn')
    await renderApp('/change-password')
    expect(await screen.findByLabelText('Mật khẩu hiện tại')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeTruthy()
  })
})

describe('AC-001.5: hết phiên khi đang soạn câu hỏi', () => {
  it('về /login (giữ bản nháp), đăng nhập lại → ô nhập có lại nội dung đã gõ', async () => {
    resetMockState('lan@congty.vn')
    const { router } = await renderApp('/c')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Câu hỏi'), 'Thời hạn thanh toán là bao lâu?')

    server.use(http.get('*/api/documents', () => HttpResponse.json({ error: { code: 'SESSION_EXPIRED', message_key: 'errors.session_expired', details: {} } }, { status: 401 })))
    resetMockState(null)
    await request('GET', '/api/documents').catch(() => undefined)
    await waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
    expect(router.currentRoute.value.query).toMatchObject({ next: '/c', reason: 'expired' })
    expect(await screen.findByText(/Phiên làm việc đã hết hạn/)).toBeTruthy()

    await login('lan@congty.vn')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/c'))
    const box = (await screen.findByLabelText('Câu hỏi')) as HTMLTextAreaElement
    await waitFor(() => expect(box.value).toBe('Thời hạn thanh toán là bao lâu?'))
  })
})

describe('đăng xuất', () => {
  it('xóa cache và bản nháp, về /login', async () => {
    resetMockState('lan@congty.vn')
    const { router } = await renderApp('/c')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Câu hỏi'), 'nháp bí mật')
    await new Promise((r) => setTimeout(r, DRAFT_DEBOUNCE_MS + 50))
    expect(Object.keys(sessionStorage).some((k) => k.startsWith('draft:'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Tài khoản Lan' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Đăng xuất' }))
    await waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
    expect(Object.keys(sessionStorage).some((k) => k.startsWith('draft:'))).toBe(false)
    await router.push('/c')
    expect(router.currentRoute.value.name).toBe('login')
  })
})

describe('trang lỗi và khả năng tiếp cận', () => {
  it('404 cho đường dẫn lạ; 403 khi API trả FORBIDDEN', async () => {
    resetMockState('lan@congty.vn')
    const { router } = await renderApp('/khong-co')
    expect(await screen.findByText('Không tìm thấy trang hoặc hội thoại')).toBeTruthy()
    server.use(http.get('*/api/documents', () => HttpResponse.json({ error: { code: 'FORBIDDEN', message_key: 'errors.forbidden', details: {} } }, { status: 403 })))
    await router.push('/c')
    await request('GET', '/api/documents').catch(() => undefined)
    await waitFor(() => expect(router.currentRoute.value.name).toBe('forbidden'))
    expect(await screen.findByText('Bạn không có quyền truy cập trang này')).toBeTruthy()
  })

  it.each(['/login', '/change-password?mode=required', '/500'])('axe-core %s: 0 lỗi serious/critical', async (path) => {
    resetMockState(path.startsWith('/change') ? 'moi@congty.vn' : null)
    const { container } = await renderApp(path)
    await screen.findByRole('heading', { level: 1 })
    await noSeriousA11yViolations(container)
  })

  it('axe-core khung ứng dụng /c', async () => {
    resetMockState('lan@congty.vn')
    const { container } = await renderApp('/c')
    await screen.findByLabelText('Câu hỏi')
    expect(screen.getByRole('link', { name: 'Bỏ qua tới ô nhập câu hỏi' }).getAttribute('href')).toBe('#composer')
    await noSeriousA11yViolations(container)
  })
})
