/** Thông tin hiển thị của một chip `[n]` (feature chat dựng từ sự kiện `citation`). */
export interface ChipInfo {
  /** aria-label, ví dụ "Trích dẫn 1: HopDong.pdf, trang 12". */
  label: string
  /** title (tooltip). */
  title: string
  /** Tài liệu đã bị xóa → kiểu gạch ngang. */
  gone: boolean
}

const MARK = /\[(\d{1,3})\]/g
export const CHIP_CLASS = 'md-cite'
export const CURSOR_CLASS = 'md-cursor'

/**
 * Biến `[n]` trong nút văn bản (đã làm sạch) thành nút trích dẫn — CHỈ khi `chips` có `n`; chưa có
 * dữ liệu hoặc ngoài phạm vi → giữ chữ thường. Không đụng tới `code`/`pre`. Phần tử được tạo bằng
 * DOM API (không chuỗi HTML) từ dữ liệu của ứng dụng nên không mở đường XSS.
 */
export function decorateCitations(root: HTMLElement, chips: ReadonlyMap<number, ChipInfo>): void {
  root.querySelectorAll<HTMLButtonElement>(`button.${CHIP_CLASS}`).forEach((b) => {
    const info = chips.get(Number(b.dataset.cite))
    if (info) applyChip(b, info)
  })
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode: (n) => (n.parentElement?.closest(`code, pre, button.${CHIP_CLASS}`) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
  })
  const nodes: Text[] = []
  while (walker.nextNode()) nodes.push(walker.currentNode as Text)
  for (const node of nodes) {
    const text = node.data
    MARK.lastIndex = 0
    if (!MARK.test(text)) continue
    MARK.lastIndex = 0
    const frag = document.createDocumentFragment()
    let last = 0
    let changed = false
    for (const m of text.matchAll(MARK)) {
      const info = chips.get(Number(m[1]))
      if (!info) continue
      changed = true
      frag.append(text.slice(last, m.index))
      const b = document.createElement('button')
      b.type = 'button'
      b.className = CHIP_CLASS
      b.dataset.cite = m[1]
      b.textContent = m[1]
      applyChip(b, info)
      frag.append(b)
      last = (m.index ?? 0) + m[0].length
    }
    if (!changed) continue
    frag.append(text.slice(last))
    node.replaceWith(frag)
  }
}

function applyChip(b: HTMLButtonElement, info: ChipInfo): void {
  b.setAttribute('aria-label', info.label)
  b.title = info.title
  b.classList.toggle('gone', info.gone)
}

/** Đặt con trỏ nhấp nháy ở cuối phần tử inline cuối cùng (mục cuối nếu là danh sách). */
export function placeCursor(root: HTMLElement, on: boolean): void {
  root.querySelectorAll(`.${CURSOR_CLASS}`).forEach((e) => e.classList.remove(CURSOR_CLASS))
  if (!on) return
  let el: Element | null = root.lastElementChild
  while (el && el.lastElementChild && ['UL', 'OL', 'LI', 'BLOCKQUOTE', 'DIV', 'TABLE', 'TBODY', 'TR'].includes(el.tagName)) {
    const child: Element = el.lastElementChild
    if (el.tagName === 'LI' && !['P', 'UL', 'OL'].includes(child.tagName)) break
    el = child
  }
  ;(el ?? root).classList.add(CURSOR_CLASS)
}
