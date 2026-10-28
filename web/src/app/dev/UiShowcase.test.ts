import { render } from '@testing-library/vue'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import UiShowcase from './UiShowcase.vue'

// axe-core trên /dev/ui (T02 mục 5). jsdom không tính được màu nên color-contrast được kiểm riêng ở tokens.test.ts;
// T21 chạy lại axe trong trình duyệt thật bằng Playwright.
describe('/dev/ui', () => {
  it('axe-core: 0 lỗi serious/critical', async () => {
    const { container } = render(UiShowcase, { global: { stubs: { RouterLink: true } } })
    const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    const blocking = result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    expect(blocking.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
  })

  it('hiển thị đủ component ở các trạng thái', () => {
    const { getAllByRole, getByText } = render(UiShowcase)
    expect(getAllByRole('progressbar')).toHaveLength(3)
    expect(getByText('Email không hợp lệ')).toBeTruthy()
    expect(getAllByRole('button').some((b) => b.hasAttribute('disabled'))).toBe(true)
    expect(getAllByRole('button').some((b) => b.getAttribute('aria-busy') === 'true')).toBe(true)
  })
})
