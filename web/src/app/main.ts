import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import { router } from './router'
import { queryClient } from './queryClient'
import { installSessionHandling } from './session'
import { useLayoutStore } from './layoutStore'
import { i18n } from '@/shared/lib/i18n'
import { startAnalytics } from '@/shared/lib/analytics'
import '@/shared/ui/base.css'

async function bootstrap() {
  // `npm run dev:mock`: API giả MSW thay backend (T06); không có trong bản build production.
  if (import.meta.env.MODE === 'mock') {
    const { startMockWorker } = await import('@/mocks/browser')
    await startMockWorker()
  }
  const app = createApp(App)
  app.use(createPinia()).use(VueQueryPlugin, { queryClient }).use(i18n)
  useLayoutStore().init()
  installSessionHandling(router)
  app.use(router)
  startAnalytics()
  app.mount('#app')
}

void bootstrap()
