import type { Router } from 'vue-router'
import { authApi } from '@/features/auth'
import { setGlobalErrorHandler, setNewVersionHandler, type ApiError } from '@/shared/api'
import { clearAllDrafts, flushDrafts } from '@/shared/lib/draft'
import { runResets } from '@/shared/lib/reset'
import { t } from '@/shared/lib/i18n'
import { toast } from '@/shared/ui'
import { queryClient } from './queryClient'

/** Xóa cache TanStack Query và reset store — không đụng tới bản nháp. */
export function resetAppState(): void {
  queryClient.clear()
  runResets()
}

function currentPath(router: Router): string {
  return router.currentRoute.value.fullPath
}

/**
 * Xử lý lỗi toàn cục của lớp API (T05 mục 3.1):
 * SESSION_EXPIRED / UNAUTHENTICATED → /login?next=… (giữ nháp), PASSWORD_CHANGE_REQUIRED →
 * đổi mật khẩu, FORBIDDEN → trang 403.
 */
// Đang rời phiên (đăng xuất / bị thu hồi / hết hạn): sau khi xóa cache, các truy vấn khác tải lại và nhận 401
// — bỏ qua chúng để không đè mất lý do (?reason=revoked) bằng một lần chuyển hướng thứ hai.
let leaving = false

async function leaveTo(router: Router, location: Parameters<Router['replace']>[0]): Promise<void> {
  leaving = true
  try {
    await router.replace(location)
  } finally {
    leaving = false
  }
}

export function installSessionHandling(router: Router): void {
  setGlobalErrorHandler((e: ApiError) => {
    if (leaving) return
    const path = currentPath(router)
    if (e.code === 'SESSION_EXPIRED' || e.code === 'UNAUTHENTICATED') {
      if (router.currentRoute.value.name === 'login') return
      const query: Record<string, string> = { next: path }
      if (e.code === 'SESSION_EXPIRED') query.reason = 'expired'
      leaving = true
      flushDrafts()
      resetAppState()
      void leaveTo(router, { name: 'login', query })
    } else if (e.code === 'PASSWORD_CHANGE_REQUIRED') {
      void router.replace({ name: 'change-password', query: { mode: 'required' } })
    } else if (e.code === 'FORBIDDEN') {
      void router.replace({ name: 'forbidden' })
    }
  })
  setNewVersionHandler(() => toast.show(`${t('app.new_version')} ${t('app.reload')}`))
}

/** Đăng xuất: gọi API, xóa cache + store + mọi bản nháp, về trang đăng nhập. */
export async function logout(router: Router): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    // vẫn đăng xuất phía client
  }
  leaving = true
  clearAllDrafts()
  resetAppState()
  await leaveTo(router, { name: 'login' })
}

/** Phiên bị thu hồi (SSE `session.revoked`, T11): xóa sạch kể cả bản nháp. */
export async function onSessionRevoked(router: Router): Promise<void> {
  leaving = true
  clearAllDrafts()
  resetAppState()
  await leaveTo(router, { name: 'login', query: { reason: 'revoked' } })
}
