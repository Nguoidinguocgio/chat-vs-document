import { createRouter, createWebHistory, type Router, type RouterHistory } from 'vue-router'
import { ChangePasswordPage, LoginPage } from '@/features/auth'
import AppShell from './AppShell.vue'
import ErrorPage from './ErrorPage.vue'
import { authGuard } from './guards'
import { queryClient } from './queryClient'

export function buildRouter(history: RouterHistory = createWebHistory()): Router {
  const r = createRouter({
    history,
    routes: [
      { path: '/', redirect: '/c' },
      { path: '/login', name: 'login', component: LoginPage },
      { path: '/change-password', name: 'change-password', component: ChangePasswordPage },
      { path: '/c', name: 'chat', component: AppShell },
      { path: '/c/:id', name: 'conversation', component: AppShell },
      { path: '/403', name: 'forbidden', component: ErrorPage, props: { code: '403' } },
      { path: '/500', name: 'server-error', component: ErrorPage, props: { code: '500' } },
      // Trang trình diễn component: chỉ bật ở môi trường dev (T02 mục 3.3)
      ...(import.meta.env.DEV ? [{ path: '/dev/ui', name: 'dev-ui', component: () => import('./dev/UiShowcase.vue') }] : []),
      { path: '/:pathMatch(.*)*', name: 'not-found', component: ErrorPage, props: { code: '404' } },
    ],
  })
  r.beforeEach((to) => authGuard(to, queryClient))
  return r
}

export const router = buildRouter()
