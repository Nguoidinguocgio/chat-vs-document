import { render, screen, waitFor } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import ConnectionBanner from './ConnectionBanner.vue'
import ErrorBoundary from './ErrorBoundary.vue'

describe('ErrorBoundary', () => {
  it('lỗi trong một vùng không làm sập vùng khác; Thử lại dựng lại vùng', async () => {
    let shouldThrow = true
    const Boom = defineComponent({
      setup() {
        if (shouldThrow) throw new Error('hỏng')
        return () => h('p', 'Vùng chat đã ổn')
      },
    })
    const Ok = defineComponent({ setup: () => () => h('p', 'Thanh trái vẫn chạy') })
    render(
      defineComponent({
        setup: () => () => [h(ErrorBoundary, { region: 'nav' }, () => h(Ok)), h(ErrorBoundary, { region: 'chat' }, () => h(Boom))],
      }),
    )
    expect(await screen.findByText('Đã có lỗi xảy ra. Vui lòng thử lại.')).toBeTruthy()
    expect(screen.getByText('Thanh trái vẫn chạy')).toBeTruthy()
    expect(document.querySelector('[data-region-error="chat"]')).not.toBeNull()
    shouldThrow = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Vùng chat đã ổn')).toBeTruthy()
  })
})

describe('ConnectionBanner', () => {
  it('hiện khi offline, ẩn khi online', async () => {
    render(ConnectionBanner)
    expect(screen.queryByRole('status')).toBeNull()
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
    window.dispatchEvent(new Event('offline'))
    expect(await screen.findByRole('status')).toHaveTextContent('Mất kết nối mạng')
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
    window.dispatchEvent(new Event('online'))
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull())
  })
})
