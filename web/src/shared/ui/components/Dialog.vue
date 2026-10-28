<script setup lang="ts">
import { DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { useI18n } from 'vue-i18n'
import Button from './Button.vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    confirmLabel?: string
    cancelLabel?: string
    destructive?: boolean
  }>(),
  { description: undefined, confirmLabel: undefined, cancelLabel: undefined, destructive: false },
)
const { t } = useI18n()
const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ confirm: [] }>()

function confirm() {
  emit('confirm')
  open.value = false
}
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 bg-text/40" />
      <DialogContent class="fixed left-1/2 top-1/2 w-[min(440px,92vw)] -translate-x-1/2 -translate-y-1/2 rounded-lg bg-bg p-6 shadow-e3">
        <DialogTitle class="text-lg font-semibold">{{ title }}</DialogTitle>
        <DialogDescription v-if="description" class="mt-2 text-sm text-text-muted">{{ description }}</DialogDescription>
        <div class="mt-4"><slot /></div>
        <div class="mt-6 flex justify-end gap-2">
          <Button variant="secondary" @click="open = false">{{ cancelLabel ?? t('ui.cancel') }}</Button>
          <Button :variant="destructive ? 'danger' : 'primary'" @click="confirm">{{ confirmLabel ?? t('ui.confirm') }}</Button>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
