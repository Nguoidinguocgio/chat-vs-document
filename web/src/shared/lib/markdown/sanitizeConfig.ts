import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

// Danh sách trắng NFR-S05 (T16 mục 3.3): không img/a/script/iframe/style, không thuộc tính nào.
export const ALLOWED_TAGS = ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'h3', 'h4']

// html: false → HTML thô trong Markdown thành chữ; liên kết/ảnh Markdown bị tắt hẳn ở tầng parser.
const md = new MarkdownIt({ html: false, linkify: false, typographer: false, breaks: false })
md.disable(['link', 'image', 'autolink', 'html_inline', 'html_block'])

/** Markdown → HTML đã làm sạch (chuỗi). Nội dung LLM là dữ liệu không tin cậy. */
export function renderSafe(source: string): string {
  return DOMPurify.sanitize(md.render(source), { ALLOWED_TAGS, ALLOWED_ATTR: [], KEEP_CONTENT: true })
}

/**
 * Tách văn bản thành các khối Markdown cấp cao nhất theo dòng trống (không cắt trong khối ```).
 * Khi đang stream chỉ khối cuối thay đổi → các khối trước dùng lại kết quả đã render.
 */
export function splitBlocks(source: string): string[] {
  const blocks: string[] = []
  let current: string[] = []
  let fence = false
  for (const line of source.split('\n')) {
    if (/^\s*(```|~~~)/.test(line)) fence = !fence
    if (!fence && line.trim() === '' && current.length > 0) {
      blocks.push(current.join('\n'))
      current = []
    } else if (fence || line.trim() !== '') {
      current.push(line)
    }
  }
  if (current.length > 0) blocks.push(current.join('\n'))
  return blocks
}

const cache = new Map<string, string>()
const CACHE_MAX = 500

/** {@link renderSafe} có ghi nhớ theo nội dung khối. */
export function renderBlock(block: string): string {
  const hit = cache.get(block)
  if (hit !== undefined) return hit
  const html = renderSafe(block)
  if (cache.size >= CACHE_MAX) cache.delete(cache.keys().next().value as string)
  cache.set(block, html)
  return html
}
