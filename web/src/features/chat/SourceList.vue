<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Citation } from '@/shared/api'

// "NGUỒN": chỉ các [n] thực sự xuất hiện trong văn bản và có dữ liệu trích dẫn.
defineProps<{ indices: number[]; citations: Record<number, Citation>; gone: (c: Citation) => boolean }>()
const emit = defineEmits<{ cite: [index: number] }>()
const { t } = useI18n()
</script>

<template>
  <div class="mt-3 border-t border-border pt-3" data-testid="source-list">
    <h4 class="mb-1.5 text-xs font-semibold uppercase tracking-wide text-text-muted">{{ t('chat.sources') }}</h4>
    <ul>
      <li v-for="n in indices" :key="n">
        <button
          type="button"
          class="flex py-1 text-left text-sm underline underline-offset-2"
          :class="gone(citations[n]) ? 'text-text-muted line-through' : 'text-primary'"
          @click="emit('cite', n)"
        >
          {{ t('chat.source_item', { n, name: gone(citations[n]) ? t('chat.document_deleted') : citations[n].document_name, page: citations[n].page }) }}
        </button>
      </li>
    </ul>
  </div>
</template>
