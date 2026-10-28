import { SseParser } from '@/shared/realtime/sseParser'

/** Một sự kiện SSE đã giải mã JSON. */
export interface SseEvent {
  event: string
  data: unknown
  id?: string
}

export interface ParseOptions {
  /** Gọi mỗi khi có byte đến (kể cả `: ping`) — dùng cho bộ giám sát im lặng. */
  onActivity?: () => void
  /** Hủy: dừng đọc ngay và ném `AbortError` (kể cả khi lớp mạng không tự cắt body). */
  signal?: AbortSignal
}

/**
 * Đọc luồng byte `text/event-stream` (T16 mục 3.1). `TextDecoder` ở chế độ `stream` nên ký tự
 * UTF-8 nhiều byte bị cắt giữa hai mảnh vẫn đúng. Khung có `data` không phải JSON bị bỏ qua.
 */
export async function* parseSseStream(stream: ReadableStream<Uint8Array>, opts: ParseOptions = {}): AsyncGenerator<SseEvent> {
  const reader = stream.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = new SseParser()
  const aborted = () => new DOMException('Đã hủy luồng', 'AbortError')
  const onAbort = () => void reader.cancel().catch(() => {})
  opts.signal?.addEventListener('abort', onAbort)
  try {
    for (;;) {
      if (opts.signal?.aborted) throw aborted()
      const { done, value } = await reader.read()
      if (opts.signal?.aborted) throw aborted()
      const text = done ? decoder.decode() + '\n\n' : decoder.decode(value, { stream: true })
      if (!done) opts.onActivity?.()
      for (const m of parser.push(text)) {
        let data: unknown
        try {
          data = JSON.parse(m.data)
        } catch {
          if (import.meta.env.DEV) console.warn(`[sse] bỏ khung "${m.event}": data không phải JSON`)
          continue
        }
        yield { event: m.event, data, ...(m.id ? { id: m.id } : {}) }
      }
      if (done) return
    }
  } finally {
    opts.signal?.removeEventListener('abort', onAbort)
    reader.releaseLock()
  }
}
