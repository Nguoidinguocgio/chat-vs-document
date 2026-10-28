<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ label: string; disabled?: boolean }>()
const el = ref<HTMLButtonElement>()
defineExpose({ el, focus: () => el.value?.focus() })
</script>

<template>
  <button
    ref="el"
    type="button"
    :aria-label="label"
    :disabled="disabled"
    class="touch-target inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted transition-colors duration-[var(--motion-fast)] hover:bg-bg-subtle hover:text-text active:bg-border disabled:cursor-not-allowed disabled:opacity-50"
  >
    <slot />
  </button>
</template>
