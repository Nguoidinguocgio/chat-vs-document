<script setup lang="ts">
import { ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'

// Ô mật khẩu có nút hiện/ẩn (prototype SCR-01/02).
const props = defineProps<{ label: string; error?: string; autocomplete: string; describedby?: string }>()
const model = defineModel<string>({ default: '' })
const { t } = useI18n()
const id = useId()
const visible = ref(false)
const el = ref<HTMLInputElement>()
defineExpose({ focus: () => el.value?.focus() })
const describedBy = () => [props.describedby, props.error ? `${id}-err` : undefined].filter(Boolean).join(' ') || undefined
</script>

<template>
  <div class="flex flex-col gap-1">
    <label :for="id" class="text-sm font-medium">{{ label }}</label>
    <div class="relative">
      <input
        :id="id"
        ref="el"
        v-model="model"
        :type="visible ? 'text' : 'password'"
        :autocomplete="autocomplete"
        :aria-invalid="error ? true : undefined"
        :aria-describedby="describedBy()"
        class="min-h-[40px] w-full rounded-md border bg-bg py-2 pl-3 pr-11 text-sm"
        :class="error ? 'border-danger' : 'border-border-strong'"
      />
      <button
        type="button"
        class="touch-target absolute right-1 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-bg-subtle"
        :aria-label="visible ? t('auth.hide_password') : t('auth.show_password')"
        :aria-pressed="visible"
        @click="visible = !visible"
      >
        <span aria-hidden="true">{{ visible ? '🙈' : '👁' }}</span>
      </button>
    </div>
    <p v-if="error" :id="`${id}-err`" class="text-xs text-danger">{{ error }}</p>
  </div>
</template>
