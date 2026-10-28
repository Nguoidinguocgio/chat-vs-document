<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Alert, Button } from '@/shared/ui'

// Lỗi trong một vùng (thanh trái, chat, bảng phải) chỉ làm hỏng vùng đó; "Thử lại" dựng lại vùng.
defineProps<{ region: string }>()
const { t } = useI18n()
const failed = ref(false)
const generation = ref(0)

onErrorCaptured((err) => {
  failed.value = true
  if (import.meta.env.DEV) console.error(err)
  return false
})

function retry() {
  failed.value = false
  generation.value++
}
</script>

<template>
  <div v-if="failed" class="p-4" :data-region-error="region">
    <Alert tone="error">
      {{ t('errors.generic') }}
      <template #action><Button size="sm" variant="secondary" @click="retry">{{ t('ui.retry') }}</Button></template>
    </Alert>
  </div>
  <slot v-else :key="generation" />
</template>
