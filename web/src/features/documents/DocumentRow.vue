<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Document } from '@/shared/api'
import { midEllipsis } from '@/shared/lib/format'
import DocumentStatus from './DocumentStatus.vue'
import { relativeDay } from './time'
import type { UploadTask } from './upload/queue'

// Một hàng tài liệu (CMP-11): 📄 tên rút gọn giữa (title = tên đầy đủ), "{n} trang · người tải · thời điểm", trạng thái.
const props = defineProps<{ doc?: Document; task?: UploadTask }>()
const emit = defineEmits<{ retry: []; retryUpload: []; cancelUpload: [] }>()
const { t } = useI18n()

const name = computed(() => props.doc?.name ?? props.task?.file.name ?? '')
const meta = computed(() => {
  const d = props.doc
  if (!d) return ''
  return [d.page_count ? t('documents.meta_pages', { n: d.page_count }) : null, d.uploaded_by.display_name, relativeDay(d.uploaded_at)]
    .filter(Boolean)
    .join(' · ')
})
</script>

<template>
  <li class="flex flex-col gap-1 border-b border-border px-1 py-3 last:border-b-0" :data-doc-id="doc?.id">
    <div class="flex min-w-0 items-center gap-2 text-sm">
      <span aria-hidden="true">📄</span>
      <b class="min-w-0 truncate font-medium" :title="name">{{ midEllipsis(name) }}</b>
    </div>
    <div v-if="meta" class="text-xs text-text-muted">{{ meta }}</div>
    <DocumentStatus :doc="doc" :task="task" @retry="emit('retry')" @retry-upload="emit('retryUpload')" @cancel-upload="emit('cancelUpload')" />
  </li>
</template>
