<script setup lang="ts">
import { ref, useId } from 'vue'

defineProps<{ label: string; error?: string; rows?: number; placeholder?: string; disabled?: boolean }>()
const model = defineModel<string>({ default: '' })
const id = useId()
const el = ref<HTMLTextAreaElement>()
defineExpose({ el, focus: () => el.value?.focus() })
</script>

<template>
  <div class="flex flex-col gap-1">
    <label :for="id" class="text-sm font-medium text-text">{{ label }}</label>
    <textarea
      :id="id"
      ref="el"
      v-model="model"
      :rows="rows ?? 3"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-invalid="error ? true : undefined"
      :aria-describedby="error ? `${id}-err` : undefined"
      class="rounded-md border bg-bg px-3 py-2 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
      :class="error ? 'border-danger' : 'border-border-strong'"
    />
    <p v-if="error" :id="`${id}-err`" class="text-xs text-danger">{{ error }}</p>
  </div>
</template>
