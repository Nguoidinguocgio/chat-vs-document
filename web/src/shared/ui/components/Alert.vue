<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'warning' | 'error' }>(), { tone: 'info' })
const map = {
  info: { box: 'bg-primary-bg border-primary text-text', icon: 'ℹ️' },
  warning: { box: 'bg-warning-bg border-warning text-text', icon: '⚠️' },
  error: { box: 'bg-danger-bg border-danger text-text', icon: '⛔' },
}
</script>

<template>
  <div :role="tone === 'error' ? 'alert' : 'status'" class="flex items-start gap-3 rounded-md border p-3 text-sm" :class="map[tone].box">
    <span aria-hidden="true">{{ map[tone].icon }}</span>
    <div class="flex-1"><slot /></div>
    <div v-if="$slots.action"><slot name="action" /></div>
  </div>
</template>
