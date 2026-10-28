<script setup lang="ts">
import { ref, useId } from 'vue'

defineProps<{ label: string; error?: string; type?: string; placeholder?: string; disabled?: boolean; autocomplete?: string }>()
const model = defineModel<string>({ default: '' })
const id = useId()
const el = ref<HTMLInputElement>()
defineExpose({ el, focus: () => el.value?.focus() })
</script>

<template>
  <div class="flex flex-col gap-1">
    <label :for="id" class="text-sm font-medium text-text">{{ label }}</label>
    <input
      :id="id"
      ref="el"
      v-model="model"
      :type="type ?? 'text'"
      :placeholder="placeholder"
      :disabled="disabled"
      :autocomplete="autocomplete"
      :aria-invalid="error ? true : undefined"
      :aria-describedby="error ? `${id}-err` : undefined"
      class="min-h-[40px] rounded-md border bg-bg px-3 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
      :class="error ? 'border-danger' : 'border-border-strong'"
    />
    <p v-if="error" :id="`${id}-err`" class="text-xs text-danger">{{ error }}</p>
  </div>
</template>
