<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Document } from '@/shared/api'
import { Button, ProgressBar } from '@/shared/ui'
import type { UploadTask } from './upload/queue'

// Trạng thái một hàng (T11 mục 3.3) — luôn icon + chữ, không chỉ màu.
const props = defineProps<{ doc?: Document; task?: UploadTask }>()
const emit = defineEmits<{ retry: []; retryUpload: []; cancelUpload: [] }>()
const { t } = useI18n()

const FAILURE_KEYS: Record<string, string> = {
  TOO_MANY_PAGES: 'documents.too_many_pages',
  PASSWORD_PROTECTED: 'documents.password_protected',
  SCANNED_PDF: 'documents.scanned',
  CORRUPTED: 'documents.corrupted',
  SYSTEM_ERROR: 'documents.system_error',
}

const failure = computed(() => {
  const d = props.doc
  if (!d || d.status !== 'failed') return null
  const key = FAILURE_KEYS[d.error_code ?? 'SYSTEM_ERROR'] ?? 'documents.system_error'
  const n = d.error_details?.page_count ?? d.page_count
  return t(key, { n })
})
const unreadable = computed(() => {
  const pages = props.doc?.unreadable_pages ?? []
  return pages.length ? t('documents.partial_unreadable', { k: pages.length, pages: pages.join(', ') }) : null
})
</script>

<template>
  <div class="flex flex-col gap-1 text-xs" data-testid="doc-status">
    <template v-if="task">
      <template v-if="task.phase === 'interrupted'">
        <span class="font-medium text-warning"><span aria-hidden="true">⚠ </span>{{ t('documents.status_interrupted') }}</span>
        <div class="flex gap-2">
          <Button size="sm" variant="secondary" @click="emit('retryUpload')">{{ t('documents.retry') }}</Button>
          <Button size="sm" variant="ghost" @click="emit('cancelUpload')">{{ t('documents.cancel_upload') }}</Button>
        </div>
      </template>
      <span v-else-if="task.phase === 'retrying'" class="font-medium text-warning">
        <span aria-hidden="true">⚠ </span>{{ t('documents.status_retrying', { k: task.attempts }) }}
      </span>
      <template v-else>
        <span class="font-medium text-primary"><span aria-hidden="true">⬆ </span>{{ t('documents.status_uploading', { p: task.progress }) }}</span>
        <ProgressBar :value="task.progress" :label="t('documents.upload_progress', { name: task.file.name })" />
      </template>
    </template>
    <template v-else-if="doc">
      <span v-if="doc.status === 'processing'" class="inline-flex items-center gap-1 font-medium text-text-muted">
        <span class="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none" aria-hidden="true" />
        {{ t('documents.status_processing') }}
      </span>
      <template v-else-if="doc.status === 'ready'">
        <span class="font-medium text-success"><span aria-hidden="true">✓ </span>{{ t('documents.status_ready') }}</span>
        <span v-if="unreadable" class="text-warning"><span aria-hidden="true">ⓘ </span>{{ unreadable }}</span>
      </template>
      <template v-else>
        <span class="font-medium text-danger"><span aria-hidden="true">✕ </span>{{ t('documents.status_failed') }}</span>
        <span class="text-danger"><span aria-hidden="true">ⓘ </span>{{ failure }}</span>
        <div v-if="doc.error_code === 'SYSTEM_ERROR'">
          <Button size="sm" variant="secondary" @click="emit('retry')"><span aria-hidden="true">↻ </span>{{ t('documents.retry') }}</Button>
        </div>
      </template>
    </template>
  </div>
</template>
