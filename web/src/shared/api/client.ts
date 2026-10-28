import { ApiError, toApiError } from './errors'

export type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  body?: unknown
  signal?: AbortSignal
  /** Gửi được cả khi trang đang đóng (analytics). */
  keepalive?: boolean
  /** Không gọi bộ xử lý lỗi toàn cục (ví dụ trang đăng nhập tự hiển thị 401). */
  silent?: boolean
}

/**
 * Bộ xử lý lỗi toàn cục do lớp `app` đăng ký (chuyển trang khi hết phiên, bắt đổi mật khẩu, 403):
 * `shared` không được import `app` (luật R-1) nên đảo chiều phụ thuộc bằng callback.
 */
type GlobalErrorHandler = (e: ApiError) => void
let onGlobalError: GlobalErrorHandler = () => {}
let onNewVersion: (version: string) => void = () => {}
let knownVersion: string | null = null

export function setGlobalErrorHandler(fn: GlobalErrorHandler): void {
  onGlobalError = fn
}

export function setNewVersionHandler(fn: (version: string) => void): void {
  onNewVersion = fn
}

const GLOBAL_CODES = new Set(['SESSION_EXPIRED', 'UNAUTHENTICATED', 'PASSWORD_CHANGE_REQUIRED', 'FORBIDDEN'])

/** Cho lớp gọi mạng không qua {@link request} (upload bằng XHR) dùng chung xử lý lỗi toàn cục. */
export function reportApiError(e: ApiError): void {
  if (GLOBAL_CODES.has(e.code)) onGlobalError(e)
}
const UNSAFE: ReadonlySet<Method> = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function noteVersion(res: Response): void {
  const v = res.headers.get('X-App-Version')
  if (!v) return
  if (knownVersion !== null && v !== knownVersion) onNewVersion(v)
  knownVersion = v
}

/**
 * Cách duy nhất để gọi API (T05 mục 3.1): cookie cùng origin, `Accept: application/json`,
 * `X-Requested-With: fetch` cho yêu cầu thay đổi dữ liệu, lỗi chuẩn hóa thành {@link ApiError}.
 */
export async function request<T>(method: Method, path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (UNSAFE.has(method)) headers['X-Requested-With'] = 'fetch'
  let body: BodyInit | undefined
  if (opts.body instanceof FormData) {
    body = opts.body
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }
  let res: Response
  try {
    // URL tuyệt đối cùng origin (fetch của Node trong test không nhận đường dẫn tương đối).
    res = await fetch(new URL(path, window.location.origin), { method, headers, body, credentials: 'same-origin', signal: opts.signal, keepalive: opts.keepalive })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw ApiError.network()
  }
  noteVersion(res)
  if (!res.ok) {
    const err = await toApiError(res)
    if (!opts.silent && GLOBAL_CODES.has(err.code)) onGlobalError(err)
    throw err
  }
  if (res.status === 204) return undefined as T
  const type = res.headers.get('Content-Type') ?? ''
  return (type.includes('json') ? await res.json() : undefined) as T
}

/** Backend có chạy không (trang chẩn đoán). */
export async function getHealth(): Promise<boolean> {
  try {
    const res = await fetch('/healthz')
    return res.ok
  } catch {
    return false
  }
}
