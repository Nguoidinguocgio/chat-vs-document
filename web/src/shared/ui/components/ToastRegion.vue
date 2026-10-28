<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { dismissToast, toasts } from './toast'

defineProps<{ closeLabel?: string }>()
const { t } = useI18n()
const infos = computed(() => toasts.filter((x) => x.kind === 'info'))
const errors = computed(() => toasts.filter((x) => x.kind === 'error'))
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(360px,92vw)] flex-col gap-2">
    <div aria-live="polite" class="flex flex-col gap-2">
      <div v-for="item in infos" :key="item.id" class="pointer-events-auto rounded-md border border-border bg-bg p-3 text-sm shadow-e2">{{ item.text }}</div>
    </div>
    <div aria-live="assertive" class="flex flex-col gap-2">
      <div v-for="item in errors" :key="item.id" role="alert" class="pointer-events-auto flex items-start justify-between gap-2 rounded-md border border-danger bg-danger-bg p-3 text-sm text-danger shadow-e2">
        <span>{{ item.text }}</span>
        <button type="button" :aria-label="closeLabel ?? t('ui.close')" class="font-medium" @click="dismissToast(item.id)">✕</button>
      </div>
    </div>
  </div>
</template>
