import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import userEvent from '@testing-library/user-event'
import { defineComponent, h, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import Alert from './Alert.vue'
import Button from './Button.vue'
import Dialog from './Dialog.vue'
import Popover from './Popover.vue'
import Spinner from './Spinner.vue'
import ToastRegion from './ToastRegion.vue'
import Tooltip from './Tooltip.vue'

function dialogHarness(destructive = false) {
  return defineComponent({
    setup() {
      const open = ref(false)
      const confirmed = ref(0)
      return () => [
        h(Button, { onClick: () => (open.value = true) }, () => 'Mở'),
        h(Dialog, {
          open: open.value,
          'onUpdate:open': (v: boolean) => (open.value = v),
          onConfirm: () => confirmed.value++,
          title: 'Xóa tài liệu',
          description: 'Không thể hoàn tác.',
          confirmLabel: 'Xóa',
          destructive,
        }),
        h('output', { 'data-testid': 'confirmed' }, String(confirmed.value)),
      ]
    },
  })
}

describe('Dialog', () => {
  it('có tiêu đề + mô tả, Esc đóng và trả focus về nút mở', async () => {
    const user = userEvent.setup()
    render(dialogHarness())
    const opener = screen.getByRole('button', { name: 'Mở' })
    await user.click(opener)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAccessibleName('Xóa tài liệu')
    expect(dialog).toHaveAccessibleDescription('Không thể hoàn tác.')
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await waitFor(() => expect(document.activeElement).toBe(opener))
  })

  it('bẫy focus trong hộp thoại', async () => {
    const user = userEvent.setup()
    render(dialogHarness())
    await user.click(screen.getByRole('button', { name: 'Mở' }))
    const dialog = await screen.findByRole('dialog')
    for (let i = 0; i < 4; i++) {
      await user.tab()
      expect(dialog.contains(document.activeElement)).toBe(true)
    }
  })

  it('biến thể phá hủy dùng nút danger; nút mặc định lấy chữ từ i18n', async () => {
    const user = userEvent.setup()
    render(dialogHarness(true))
    await user.click(screen.getByRole('button', { name: 'Mở' }))
    expect((await screen.findByRole('button', { name: 'Xóa' })).className).toContain('bg-danger')
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Xóa' }))
    expect(screen.getByTestId('confirmed').textContent).toBe('1')
  })
})

describe('Popover', () => {
  it('mở bằng bàn phím và đóng bằng Esc', async () => {
    const user = userEvent.setup()
    render(Popover, { slots: { trigger: '<button type="button">Nguồn</button>', default: 'Trang 4' } })
    screen.getByRole('button', { name: 'Nguồn' }).focus()
    await user.keyboard('{Enter}')
    expect(await screen.findByText('Trang 4')).toBeTruthy()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByText('Trang 4')).toBeNull())
  })
})

describe('Tooltip', () => {
  it('hiện khi focus (không chỉ khi hover)', async () => {
    render(Tooltip, { props: { text: 'Đóng bảng' }, slots: { default: '<button type="button" aria-label="Đóng">x</button>' } })
    await fireEvent.focus(screen.getByRole('button', { name: 'Đóng' }))
    expect((await screen.findAllByText('Đóng bảng')).length).toBeGreaterThan(0)
  })
})

describe('Spinner / Alert / ToastRegion', () => {
  it('Spinner có role=status và nhãn ẩn mặc định từ i18n', () => {
    render(Spinner)
    expect(screen.getByRole('status').textContent).toContain('Đang tải')
  })
  it('Alert lỗi dùng role=alert, có icon và nút hành động', () => {
    render(Alert, { props: { tone: 'error' }, slots: { default: 'Không thể xử lý', action: '<button type="button">Thử lại</button>' } })
    expect(screen.getByRole('alert').textContent).toContain('Không thể xử lý')
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeTruthy()
  })
  it('ToastRegion có vùng polite và assertive', () => {
    const { container } = render(ToastRegion)
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull()
    expect(container.querySelector('[aria-live="assertive"]')).not.toBeNull()
  })
})
