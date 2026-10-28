<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDocuments, useUploadQueue } from '@/features/documents'
import type { CurrentUser } from '@/shared/api'
import { IconButton } from '@/shared/ui'
import AccountMenu from './AccountMenu.vue'
import { useLayoutStore } from './layoutStore'

defineProps<{ user: CurrentUser }>()
const { t } = useI18n()
const layout = useLayoutStore()
const { data } = useDocuments()
const queue = useUploadQueue()
const used = computed(() => (data.value?.count ?? 0) + queue.tasks.length)
</script>

<template>
  <header class="topbar flex items-center gap-2 border-b border-border bg-bg px-3">
    <IconButton class="nav-toggle" :label="layout.navOpen ? t('app.close_navigation') : t('app.open_navigation')" :aria-expanded="layout.navOpen" aria-controls="app-nav" @click="layout.navOpen = !layout.navOpen">
      <span aria-hidden="true">☰</span>
    </IconButton>
    <span class="flex items-center gap-2 font-semibold">
      <svg width="24" height="24" viewBox="0 0 28 28" aria-hidden="true" class="text-primary"><rect width="28" height="28" rx="7" fill="currentColor" /><path d="M8 7h8l4 4v10H8z" class="fill-bg" /></svg>
      {{ t('app.name') }}
    </span>
    <span class="flex-1" />
    <button
      type="button"
      data-testid="doc-counter"
      class="touch-target inline-flex min-h-[32px] items-center gap-1 rounded-md border px-2 text-sm"
      :class="used >= 18 ? 'border-warning bg-warning-bg text-text' : 'border-border text-text hover:bg-bg-subtle'"
      :aria-label="t('documents.count_label', { n: used })"
      :aria-expanded="layout.panel === 'documents'"
      aria-controls="app-sources"
      @click="layout.togglePanel('documents')"
    >
      <span aria-hidden="true">📄</span>{{ t('documents.count', { n: used }) }}
    </button>
    <AccountMenu :user="user" />
  </header>
</template>

<style scoped>
.topbar {
  height: 56px;
}
@media (min-width: 1024px) {
  .nav-toggle {
    display: none;
  }
}
</style>
