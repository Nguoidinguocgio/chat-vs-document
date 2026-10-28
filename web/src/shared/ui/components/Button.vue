<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
    size?: 'sm' | 'md'
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', loading: false, disabled: false, type: 'button' },
)

const el = ref<HTMLButtonElement>()
// Tương đương forwardRef: cha lấy phần tử qua ref của component.
defineExpose({ el, focus: () => el.value?.focus() })

const classes = computed(() => [
  'touch-target inline-flex items-center justify-center gap-2 rounded-md font-medium',
  'transition-colors duration-[var(--motion-fast)] disabled:cursor-not-allowed disabled:opacity-50',
  props.size === 'sm' ? 'min-h-[36px] px-3 text-sm' : 'min-h-[40px] px-4 text-sm',
  {
    primary: 'bg-primary text-bg hover:bg-primary-hover active:bg-primary-hover',
    secondary: 'border border-border-strong bg-bg text-text hover:bg-bg-subtle active:bg-border',
    danger: 'bg-danger text-bg hover:opacity-90 active:opacity-80',
    ghost: 'bg-transparent text-primary hover:bg-primary-bg active:bg-primary-bg',
  }[props.variant],
])
</script>

<template>
  <button ref="el" :type="type" :class="classes" :disabled="disabled || loading" :aria-busy="loading || undefined">
    <span v-if="loading" class="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none" aria-hidden="true" />
    <slot />
  </button>
</template>
