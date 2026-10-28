import type { QueryClient } from '@tanstack/vue-query'
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { fetchCurrentUser } from '@/features/auth'

const PUBLIC = new Set(['login', 'forbidden', 'not-found', 'server-error', 'dev-ui'])

/**
 * Guard theo SDD 3.5.2: chưa đăng nhập → /login?next=…; đã đăng nhập vào /login → /c;
 * `must_change_password` → chỉ vào được /change-password.
 */
export async function authGuard(to: RouteLocationNormalized, qc: QueryClient): Promise<true | RouteLocationRaw> {
  const name = String(to.name ?? '')
  const user = await fetchCurrentUser(qc)
  if (name === 'login') return user ? (user.must_change_password ? { name: 'change-password', query: { mode: 'required' } } : '/c') : true
  if (PUBLIC.has(name)) return true
  if (!user) return { name: 'login', query: { next: to.fullPath } }
  if (user.must_change_password && name !== 'change-password') return { name: 'change-password', query: { mode: 'required' } }
  return true
}
