<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button } from '@/shared/ui'

// SCR-06 Trang lỗi 403 / 404 / 500.
const props = defineProps<{ code: '403' | '404' | '500' }>()
const { t } = useI18n()
const router = useRouter()
const icon = computed(() => ({ '403': '🔒', '404': '🔎', '500': '⚠️' })[props.code])
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center gap-3 p-6 text-center">
    <div class="text-[48px]" aria-hidden="true">{{ icon }}</div>
    <span class="text-xs font-semibold tracking-wide text-text-muted">{{ t('app.error_code', { code }) }}</span>
    <h1 class="text-xl font-semibold">{{ t(`app.e${code}_title`) }}</h1>
    <p class="max-w-md text-sm text-text-muted">{{ t(`app.e${code}_message`) }}</p>
    <div class="mt-2 flex gap-2">
      <Button v-if="code === '500'" @click="router.go(0)">{{ t('app.reload_page') }}</Button>
      <Button :variant="code === '500' ? 'secondary' : 'primary'" @click="router.push('/c')">{{ t('app.to_chat') }}</Button>
    </div>
  </main>
</template>
