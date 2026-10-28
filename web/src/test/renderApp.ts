import { render } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory } from 'vue-router'
import App from '@/app/App.vue'
import { useLayoutStore } from '@/app/layoutStore'
import { queryClient } from '@/app/queryClient'
import { buildRouter } from '@/app/router'
import { installSessionHandling } from '@/app/session'
import { i18n } from '@/shared/lib/i18n'

/** Dựng toàn bộ ứng dụng ở `path` (router bộ nhớ, API giả MSW do test tự bật). */
export async function renderApp(path: string) {
  queryClient.clear()
  const router = buildRouter(createMemoryHistory())
  const pinia = createPinia()
  installSessionHandling(router)
  const utils = render(App, {
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }], i18n, router],
    },
  })
  useLayoutStore(pinia).init()
  await router.push(path)
  await router.isReady()
  return { ...utils, router }
}
