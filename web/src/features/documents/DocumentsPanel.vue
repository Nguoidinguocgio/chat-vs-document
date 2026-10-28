<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Alert, Badge, IconButton, Skeleton } from '@/shared/ui'
import DocumentRow from './DocumentRow.vue'
import { MAX_DOCS } from './api'
import { statusAnnouncement, useDocuments, useRetryDocument } from './useDocuments'
import RejectionList from './upload/RejectionList.vue'
import UploadDropzone from './upload/UploadDropzone.vue'
import { useUploadQueue } from './upload/queue'

// Bảng Tài liệu (T11 mục 3.3): huy hiệu x/20 (cảnh báo từ 18), vùng tải lên, file bị từ chối,
// danh sách gồm cả file đang tải lên (ở đầu) và tài liệu của workspace.
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const { data, isPending, isError, refetch } = useDocuments()
const queue = useUploadQueue()
const retryDoc = useRetryDocument()

const docs = computed(() => data.value?.items ?? [])
const used = computed(() => (data.value?.count ?? 0) + queue.tasks.length)
const full = computed(() => used.value >= MAX_DOCS)
</script>

<template>
  <section class="flex h-full flex-col" aria-labelledby="documents-title">
    <header class="flex items-center gap-2 border-b border-border px-4 py-3">
      <h2 id="documents-title" class="text-base font-semibold">{{ t('documents.panel_title') }}</h2>
      <Badge :tone="used >= 18 ? 'warning' : 'neutral'" data-testid="doc-count" :aria-label="t('documents.count_label', { n: used })">
        {{ t('documents.count', { n: used }) }}
      </Badge>
      <span class="flex-1" />
      <IconButton :label="t('documents.close_panel')" @click="emit('close')"><span aria-hidden="true">✕</span></IconButton>
    </header>
    <div class="flex flex-1 flex-col gap-3 overflow-y-auto p-4">
      <UploadDropzone :full="full" />
      <RejectionList />
      <div v-if="isPending" class="flex flex-col gap-2"><Skeleton height="48px" /><Skeleton height="48px" /></div>
      <Alert v-else-if="isError" tone="error">
        {{ t('errors.generic') }}
        <template #action><button type="button" class="text-sm underline" @click="refetch()">{{ t('ui.retry') }}</button></template>
      </Alert>
      <template v-else>
        <p v-if="!docs.length && !queue.tasks.length" class="mt-4 text-center text-sm text-text-muted">{{ t('documents.empty_list') }}</p>
        <ul :aria-label="t('documents.list_label')" class="flex flex-col">
          <DocumentRow
            v-for="task in queue.tasks"
            :key="task.localId"
            :task="task"
            @retry-upload="queue.retry(task.localId)"
            @cancel-upload="queue.cancel(task.localId)"
          />
          <DocumentRow v-for="d in docs" :key="d.id" :doc="d" @retry="retryDoc(d)" />
        </ul>
      </template>
      <p class="sr-only-text" aria-live="polite">{{ statusAnnouncement }}</p>
    </div>
  </section>
</template>
