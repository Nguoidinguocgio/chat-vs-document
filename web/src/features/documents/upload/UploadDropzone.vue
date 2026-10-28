<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/shared/ui'
import { pickerRequests } from './picker'
import { useUpload } from './useUpload'

// Vùng kéo-thả + nút "Chọn file" (T10 mục 3.2). Đủ 20 tài liệu: khóa vùng và hiện documents.limit_reached.
defineProps<{ full: boolean }>()
const { t } = useI18n()
const { addFiles } = useUpload()
const input = ref<HTMLInputElement>()
const over = ref(false)

watch(pickerRequests, () => input.value?.click())

async function onPick(e: Event) {
  const el = e.target as HTMLInputElement
  await addFiles(el.files)
  el.value = ''
}

async function onDrop(e: DragEvent) {
  over.value = false
  await addFiles(e.dataTransfer?.files)
}
</script>

<template>
  <div
    class="flex flex-col items-center gap-1 rounded-lg border-2 border-dashed p-4 text-center text-sm"
    :class="full ? 'border-border bg-bg-subtle text-text-muted' : over ? 'border-primary bg-primary-bg' : 'border-border-strong'"
    data-testid="dropzone"
    @dragover.prevent="over = !full"
    @dragleave="over = false"
    @drop.prevent.stop="!full && onDrop($event)"
  >
    <span class="text-xl" aria-hidden="true">⬆</span>
    <template v-if="full">
      <b>{{ t('documents.full_title') }}</b>
      <span class="text-xs">{{ t('documents.limit_reached') }}</span>
    </template>
    <template v-else>
      <b>{{ t('documents.drop_here') }}</b>
      <span>
        {{ t('documents.or') }}
        <Button size="sm" variant="secondary" @click="input?.click()">{{ t('documents.choose_file') }}</Button>
      </span>
    </template>
    <span class="text-xs text-text-muted">{{ t('documents.limits') }}</span>
    <input ref="input" type="file" multiple accept="application/pdf,.pdf" class="hidden" tabindex="-1" aria-hidden="true" data-testid="file-input" @change="onPick" />
  </div>
</template>
