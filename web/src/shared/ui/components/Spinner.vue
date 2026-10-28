<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{ label?: string }>()
const { t } = useI18n()
</script>

<template>
  <span role="status" class="inline-flex items-center">
    <span class="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent motion-reduce:animate-none" aria-hidden="true" />
    <span class="sr-only-text">{{ label ?? t('ui.loading') }}</span>
  </span>
</template>
