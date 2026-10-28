/** Lỗi API đã chuẩn hóa từ định dạng `{"error":{"code","message_key","details"}}` (T05 mục 3.1). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly messageKey: string,
    readonly details: Record<string, unknown> = {},
  ) {
    super(code)
    this.name = 'ApiError'
  }

  static network(status = 0): ApiError {
    return new ApiError(status, 'NETWORK_OR_UNKNOWN', 'errors.generic')
  }
}

export function isApiError(e: unknown, code?: string): e is ApiError {
  return e instanceof ApiError && (code === undefined || e.code === code)
}

interface ErrorBody {
  error?: { code?: unknown; message_key?: unknown; details?: unknown }
}

/** Đọc phản hồi lỗi; không phải JSON đúng định dạng → `NETWORK_OR_UNKNOWN`. */
export async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as ErrorBody
    const e = body.error
    if (e && typeof e.code === 'string' && typeof e.message_key === 'string') {
      const details = e.details && typeof e.details === 'object' ? (e.details as Record<string, unknown>) : {}
      return new ApiError(res.status, e.code, e.message_key, details)
    }
  } catch {
    // body không phải JSON
  }
  return ApiError.network(res.status)
}
