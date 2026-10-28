<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info' }>(), { tone: 'neutral' })
const toneClass = {
  neutral: 'bg-bg-subtle text-text-muted border-border',
  success: 'bg-success-bg text-success border-success',
  warning: 'bg-warning-bg text-warning border-warning',
  danger: 'bg-danger-bg text-danger border-danger',
  info: 'bg-primary-bg text-primary border-primary',
}
</script>

<template>
  <span class="inline-flex items-center rounded-sm border px-2 py-0.5 text-xs font-medium" :class="toneClass[tone]"><slot /></span>
</template>
