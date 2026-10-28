import { reportApiError } from './client'
import { ApiError, toApiError } from './errors'

/**
 * Mở luồng SSE bằng POST (hỏi đáp, T16 mục 3.2) — `EventSource` không gửi được body nên dùng
 * `fetch` + `ReadableStream`. Lỗi trước khi luồng mở là JSON chuẩn → {@link ApiError}.
 */
export async function openStream(path: string, body: unknown, signal: AbortSignal): Promise<ReadableStream<Uint8Array>> {
  let res: Response
  try {
    res = await fetch(new URL(path, window.location.origin), {
      method: 'POST',
      headers: { Accept: 'text/event-stream, application/json', 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
      body: JSON.stringify(body),
      credentials: 'same-origin',
      signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw ApiError.network()
  }
  if (!res.ok) {
    const err = await toApiError(res)
    reportApiError(err)
    throw err
  }
  if (!res.body) throw ApiError.network(res.status)
  return res.body
}
