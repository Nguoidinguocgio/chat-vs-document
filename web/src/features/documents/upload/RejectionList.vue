<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { midEllipsis } from '@/shared/lib/format'
import { IconButton } from '@/shared/ui'
import { useUploadQueue } from './queue'

// File bị từ chối (T10 mục 3.4): khối đỏ role="alert", không tự biến mất, có nút ✕.
const { t } = useI18n()
const queue = useUploadQueue()
</script>

<template>
  <!-- div thay vì ul/li: role="alert" trên li phá ngữ nghĩa danh sách (axe "list") -->
  <div v-if="queue.rejections.length" class="flex flex-col gap-2">
    <div
      v-for="r in queue.rejections"
      :key="r.id"
      role="alert"
      class="flex items-start gap-2 rounded-md border border-danger bg-danger-bg p-2 text-sm text-text"
    >
      <span aria-hidden="true" class="text-danger">✕</span>
      <span class="min-w-0 flex-1">
        <b :title="r.fileName">{{ midEllipsis(r.fileName) }}:</b>
        {{ t(r.messageKey, r.params ?? {}) }}
      </span>
      <IconButton :label="t('documents.dismiss_rejection')" @click="queue.dismissRejection(r.id)"><span aria-hidden="true">✕</span></IconButton>
    </div>
  </div>
</template>
