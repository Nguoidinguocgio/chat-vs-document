import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Button from './Button.vue'
import IconButton from './IconButton.vue'
import Input from './Input.vue'
import ProgressBar from './ProgressBar.vue'
import { dismissToast, toast, toasts } from './toast'

describe('Button', () => {
  it('loading vô hiệu hóa nút và đặt aria-busy', () => {
    const w = mount(Button, { props: { loading: true }, slots: { default: 'Lưu' } })
    expect(w.attributes('disabled')).toBeDefined()
    expect(w.attributes('aria-busy')).toBe('true')
  })
  it('hiển thị chữ tiếng Việt có dấu', () => {
    expect(mount(Button, { slots: { default: 'Tải lên' } }).text()).toBe('Tải lên')
  })
})

describe('IconButton', () => {
  it('có aria-label từ prop label', () => {
    expect(mount(IconButton, { props: { label: 'Đóng' } }).attributes('aria-label')).toBe('Đóng')
  })
})

describe('Input', () => {
  it('gắn nhãn bằng for/id và aria-invalid + aria-describedby khi có lỗi', () => {
    const w = mount(Input, { props: { label: 'Email', error: 'Sai' } })
    const input = w.get('input')
    expect(w.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(w.get(`#${input.attributes('aria-describedby')}`).text()).toBe('Sai')
  })
})

describe('ProgressBar', () => {
  it('có role progressbar và aria-valuenow', () => {
    const w = mount(ProgressBar, { props: { value: 40, label: 'Tải lên' } })
    expect(w.attributes('role')).toBe('progressbar')
    expect(w.attributes('aria-valuenow')).toBe('40')
  })
})

describe('toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    toasts.splice(0)
  })
  afterEach(() => vi.useRealTimers())

  it('thông tin tự ẩn sau 5 giây', () => {
    toast.show('ok')
    expect(toasts).toHaveLength(1)
    vi.advanceTimersByTime(5000)
    expect(toasts).toHaveLength(0)
  })
  it('lỗi được giữ đến khi đóng', () => {
    toast.error('lỗi')
    vi.advanceTimersByTime(60000)
    expect(toasts).toHaveLength(1)
    dismissToast(toasts[0].id)
    expect(toasts).toHaveLength(0)
  })
  it('tối đa 3 toast', () => {
    for (let i = 0; i < 5; i++) toast.error(`e${i}`)
    expect(toasts).toHaveLength(3)
  })
})
