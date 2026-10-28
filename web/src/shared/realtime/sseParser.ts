/** Một sự kiện SSE đã phân tích (theo chuẩn text/event-stream). */
export interface SseMessage {
  event: string
  data: string
  id?: string
}

/**
 * Bộ phân tích luồng SSE tăng dần: nạp từng mẩu chữ (có thể cắt giữa dòng), nhận các sự kiện hoàn
 * chỉnh. Bỏ qua dòng chú thích (`: ping`). Dùng cho luồng hỏi đáp đọc bằng fetch (T16).
 */
export class SseParser {
  private buffer = ''
  private event = ''
  private data: string[] = []
  private id: string | undefined

  push(chunk: string): SseMessage[] {
    this.buffer += chunk
    const out: SseMessage[] = []
    let nl: number
    while ((nl = this.buffer.search(/\r\n|\n|\r/)) >= 0) {
      // `\r` ở cuối mảnh: có thể là nửa đầu của `\r\n` → chờ mảnh sau.
      if (nl === this.buffer.length - 1 && this.buffer.endsWith('\r')) break
      const line = this.buffer.slice(0, nl)
      this.buffer = this.buffer.slice(nl + (this.buffer.startsWith('\r\n', nl) ? 2 : 1))
      const msg = this.line(line)
      if (msg) out.push(msg)
    }
    return out
  }

  private line(line: string): SseMessage | null {
    if (line === '') {
      if (this.data.length === 0 && !this.event) return null
      const msg: SseMessage = { event: this.event || 'message', data: this.data.join('\n'), ...(this.id ? { id: this.id } : {}) }
      this.event = ''
      this.data = []
      return msg
    }
    if (line.startsWith(':')) return null
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '')
    if (field === 'event') this.event = value
    else if (field === 'data') this.data.push(value)
    else if (field === 'id') this.id = value
    return null
  }
}
