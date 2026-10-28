import { render } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import AnswerMarkdown from './AnswerMarkdown.vue'
import { type ChipInfo } from './citationPlugin'
import { renderSafe, splitBlocks } from './sanitizeConfig'

const chip = (n: number, gone = false): [number, ChipInfo] => [n, { label: `Trích dẫn ${n}: Tài liệu ${n}, trang ${n}`, title: `Tài liệu ${n}, trang ${n}`, gone }]

/** Bộ payload XSS (NFR-S05, QS-04) — dùng lại ở T22. */
export const XSS_PAYLOADS = [
  '<img src=x onerror=alert(1)>',
  '<script>alert(1)</script>',
  '[bấm](javascript:alert(1))',
  '<iframe src="https://evil.example"></iframe>',
  '<svg onload=alert(1)><circle r=5 /></svg>',
  '![ảnh](https://evil.example/x.png)',
  '<a href="https://evil.example">liên kết</a>',
  '<div style="background:url(javascript:alert(1))">khối HTML thô</div>\n\n<p onclick="alert(1)">x</p>',
  '[1](javascript:alert(1))',
  '<https://evil.example/autolink>',
  '<details open ontoggle=alert(1)>',
  '**đậm** <b onmouseover=alert(1)>b</b> `<img src=x onerror=alert(1)>`',
  'Chuỗi rất dài '.repeat(5000) + '<img src=x onerror=alert(1)>',
]

function assertSafe(root: Element) {
  expect(root.querySelectorAll('img, a, iframe, script, svg, style, object, embed, details, div[style]')).toHaveLength(0)
  for (const el of root.querySelectorAll('*')) {
    for (const attr of el.getAttributeNames()) {
      expect(attr, `<${el.tagName.toLowerCase()} ${attr}>`).not.toMatch(/^(on|style|href|src)/)
    }
  }
}

describe('renderSafe (markdown-it + DOMPurify, danh sách trắng)', () => {
  it.each(XSS_PAYLOADS.map((p, i) => [i, p]))('payload #%i không tạo phần tử/thuộc tính nguy hiểm', (_i, payload) => {
    const alert = vi.fn()
    vi.stubGlobal('alert', alert)
    const box = new DOMParser().parseFromString(renderSafe(payload as string), 'text/html').body
    assertSafe(box)
    expect(alert).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('giữ Markdown cơ bản: đoạn, danh sách, in đậm, code, bảng', () => {
    const html = renderSafe('**a** _b_\n\n- x\n- y\n\n```\nconst [1] = 2\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |')
    expect(html).toContain('<strong>a</strong>')
    expect(html).toContain('<em>b</em>')
    expect(html).toContain('<li>x</li>')
    expect(html).toContain('<pre><code>')
    expect(html).toContain('<table>')
  })

  it('splitBlocks: tách theo dòng trống nhưng không cắt trong khối ```', () => {
    expect(splitBlocks('a\nb\n\n- x\n\n```\nq\n\nr\n```\n\nc')).toEqual(['a\nb', '- x', '```\nq\n\nr\n```', 'c'])
  })
})

describe('AnswerMarkdown', () => {
  it('chip [n] chỉ xuất hiện khi đã có dữ liệu trích dẫn; không trong code; bấm phát sự kiện cite', async () => {
    const { container, rerender, emitted } = render(AnswerMarkdown, { props: { source: 'Thanh toán 30 ngày [1], 45 ngày [2], ngoài phạm vi [9].\n\n`mã [1]`', chips: new Map([chip(1)]) } })
    await nextTick()
    let chips = container.querySelectorAll('button.md-cite')
    expect(chips).toHaveLength(1)
    expect(chips[0].getAttribute('aria-label')).toBe('Trích dẫn 1: Tài liệu 1, trang 1')
    expect(container.textContent).toContain('[2]')
    expect(container.querySelector('code')?.textContent).toBe('mã [1]')

    // citation [2] đến sau → thành chip; tài liệu đã xóa → gạch ngang
    await rerender({ chips: new Map([chip(1), chip(2, true)]) })
    await nextTick()
    await nextTick()
    chips = container.querySelectorAll('button.md-cite')
    expect(chips).toHaveLength(2)
    expect(chips[1].classList.contains('gone')).toBe(true)
    ;(chips[1] as HTMLButtonElement).click()
    expect(emitted().cite).toEqual([[2]])
  })

  it('payload XSS đi qua component cũng an toàn (kể cả có chip)', async () => {
    const { container } = render(AnswerMarkdown, { props: { source: XSS_PAYLOADS.slice(0, 12).join('\n\n') + ' [1]', chips: new Map([chip(1)]) } })
    await nextTick()
    // chip là phần tử duy nhất có thuộc tính, do ứng dụng tạo
    container.querySelectorAll('button.md-cite').forEach((b) => b.remove())
    assertSafe(container)
  })

  it('con trỏ ở cuối phần tử inline cuối (mục cuối của danh sách) khi đang stream', async () => {
    const { container, rerender } = render(AnswerMarkdown, { props: { source: 'Mở đầu\n\n- một\n- hai', chips: new Map(), streaming: true } })
    await nextTick()
    await nextTick()
    const cursor = container.querySelectorAll('.md-cursor')
    expect(cursor).toHaveLength(1)
    expect(cursor[0].tagName).toBe('LI')
    expect(cursor[0].textContent).toBe('hai')
    await rerender({ streaming: false })
    await nextTick()
    await nextTick()
    expect(container.querySelectorAll('.md-cursor')).toHaveLength(0)
  })

  it('hiệu năng: 3.000 ký tự phát từng 2 ký tự, mỗi lần cập nhật < 50 ms và các khối cũ không bị render lại', async () => {
    const full = ('Đoạn văn có **đậm** và trích dẫn [1]. '.repeat(8) + '\n\n').repeat(10).slice(0, 3000)
    const { container, rerender } = render(AnswerMarkdown, { props: { source: '', chips: new Map([chip(1)]), streaming: true } })
    let worst = 0
    let firstBlock: Element | null = null
    for (let i = 2; i <= full.length; i += 2) {
      const t0 = performance.now()
      await rerender({ source: full.slice(0, i) })
      worst = Math.max(worst, performance.now() - t0)
      if (i === 1000) firstBlock = container.querySelector('.answer-md > div')
    }
    await nextTick()
    expect(worst).toBeLessThan(50)
    // cùng phần tử DOM với lúc đầu → khối đầu không bị thay thế
    expect(container.querySelector('.answer-md > div')).toBe(firstBlock)
  })
})
