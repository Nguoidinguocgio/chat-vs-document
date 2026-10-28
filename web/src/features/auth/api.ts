import { useQuery, type QueryClient } from '@tanstack/vue-query'
import { request, type CurrentUser } from '@/shared/api'

export const ME_KEY = ['me'] as const

export const authApi = {
  login: (email: string, password: string) =>
    request<CurrentUser>('POST', '/api/auth/login', { body: { email, password }, silent: true }),
  logout: () => request<void>('POST', '/api/auth/logout', { silent: true }),
  me: () => request<CurrentUser>('GET', '/api/me', { silent: true }),
  changePassword: (newPassword: string, currentPassword: string | null) =>
    request<void>('POST', '/api/auth/change-password', {
      body: { new_password: newPassword, ...(currentPassword !== null ? { current_password: currentPassword } : {}) },
      silent: true,
    }),
}

/** Người dùng hiện tại (`GET /api/me`) — một lần mỗi phiên, không tự thử lại. */
export function useCurrentUser() {
  return useQuery({ queryKey: ME_KEY, queryFn: authApi.me, staleTime: Infinity, retry: false })
}

/** Lấy người dùng cho router guard; `null` nếu chưa đăng nhập. */
export async function fetchCurrentUser(qc: QueryClient): Promise<CurrentUser | null> {
  try {
    return await qc.fetchQuery({ queryKey: ME_KEY, queryFn: authApi.me, staleTime: Infinity, retry: false })
  } catch {
    return null
  }
}

/** Chỉ chấp nhận đường dẫn nội bộ `/…` (không `//…`, không `/\…`) — chống open redirect. */
export function safeNext(raw: unknown, fallback = '/c'): string {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return fallback
  if (/^\/(login|change-password)(\/|\?|$)/.test(raw)) return fallback
  return raw
}

/** Chính sách mật khẩu (giống backend T04 mục 3.5). */
export function passwordRules(pw: string) {
  return { length: pw.length >= 10, letter: /\p{L}/u.test(pw), digit: /\p{Nd}/u.test(pw) }
}
