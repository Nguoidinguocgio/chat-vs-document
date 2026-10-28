<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Button, EmptyState, Spinner } from '@/shared/ui'
import { requestFilePicker } from './upload/picker'
import { useDocumentsSummary } from './useDocuments'

// Trạng thái trống của khu chat (CMP-09, phần tài liệu): chưa có tài liệu · chỉ đang xử lý · tất cả lỗi · sẵn sàng.
const emit = defineEmits<{ openDocuments: [] }>()
const { t } = useI18n()
const summary = useDocumentsSummary()

function upload() {
  emit('openDocuments')
  requestFilePicker()
}
</script>

<template>
  <div class="mx-auto flex max-w-lg flex-col items-center p-8" data-testid="empty-workspace">
    <EmptyState v-if="summary.total === 0" icon="📄" :title="t('chat.empty_title')" :description="t('chat.empty_text')">
      <div class="flex flex-col items-center gap-2">
        <Button @click="upload">{{ t('documents.upload') }}</Button>
        <span class="text-xs text-text-muted">{{ t('chat.empty_limits') }}</span>
      </div>
    </EmptyState>
    <EmptyState v-else-if="!summary.hasReady && summary.processing > 0" icon="⏳" :title="t('chat.processing_title')" :description="t('chat.all_processing', { n: summary.processing })">
      <Spinner />
    </EmptyState>
    <EmptyState v-else-if="!summary.hasReady" icon="⚠️" :title="t('chat.all_failed_title')" :description="t('chat.all_failed')">
      <Button variant="secondary" @click="emit('openDocuments')">{{ t('chat.open_documents') }}</Button>
    </EmptyState>
    <EmptyState v-else icon="💬" :title="t('chat.ready_title')" :description="t('chat.ready_text', { n: summary.ready })" />
  </div>
</template>
