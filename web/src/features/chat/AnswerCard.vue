<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDocuments } from '@/features/documents'
import type { Citation } from '@/shared/api'
import { AnswerMarkdown } from '@/shared/lib/markdown'
import { Button, Spinner } from '@/shared/ui'
import { usedCitations, type Turn } from './answerReducer'
import { chipMap } from './CitationChip'
import SourceList from './SourceList.vue'

// CMP-05: trình bày khác nhau rõ ràng cho từng trạng thái ST-02. Bất biến: chỉ answered/partial có SourceList.
const props = defineProps<{ turn: Turn }>()
const emit = defineEmits<{ cite: [messageId: string, index: number]; retry: [] }>()
const { t } = useI18n()
const { data: docs } = useDocuments()

const present = computed<Set<string> | null>(() => (docs.value ? new Set(docs.value.items.map((d) => d.id)) : null))
// Giữ nguyên Map khi trích dẫn không đổi (mỗi delta tạo `turn` mới) để AnswerMarkdown không trang trí lại mọi khối.
let memo: { citations: Turn['citations']; present: Set<string> | null; map: ReturnType<typeof chipMap> } | null = null
const chips = computed(() => {
  const c = props.turn.citations
  if (!memo || memo.citations !== c || memo.present !== present.value) memo = { citations: c, present: present.value, map: chipMap(c, present.value) }
  return memo.map
})
const gone = (c: Citation) => c.document_deleted || (present.value !== null && !present.value.has(c.document_id))
const used = computed(() => usedCitations(props.turn))
const s = computed(() => props.turn.state)
const showSources = computed(() => (s.value === 'answered' || s.value === 'partial') && used.value.length > 0)
const inProgress = computed(() => s.value === 'searching' || s.value === 'streaming')
const errorKey = computed(() => {
  if (s.value === 'timeout') return 'chat.timeout'
  if (s.value === 'interrupted') return 'chat.interrupted'
  if (s.value === 'failed') return 'chat.llm_unavailable'
  return null
})

function cite(n: number) {
  if (props.turn.messageId) emit('cite', props.turn.messageId, n)
}
</script>

<template>
  <article
    class="rounded-lg border border-border bg-bg p-4 shadow-e1"
    :lang="turn.lang"
    :aria-label="t('chat.answer_label')"
    :aria-busy="inProgress ? 'true' : 'false'"
    :data-state="turn.state"
    data-testid="answer-card"
  >
    <div v-if="s === 'searching'" class="flex items-center gap-2 text-text-muted">
      <Spinner />
      <span>{{ t('chat.searching') }}</span>
    </div>

    <div v-if="s === 'notfound'" class="flex items-start gap-2 rounded-md border border-primary bg-primary-bg px-3 py-2.5 text-sm text-text" data-testid="not-found">
      <span aria-hidden="true">ⓘ</span>
      <div>
        {{ t('chat.not_found') }}
        <div class="mt-1 text-text-muted">{{ t('chat.not_found_hint') }}</div>
      </div>
    </div>
    <AnswerMarkdown v-else-if="turn.answer" :source="turn.answer" :chips="chips" :streaming="s === 'streaming'" @cite="cite" />

    <div v-if="s === 'partial' && turn.missing" class="mt-2.5 flex items-start gap-2 rounded-md border border-primary bg-primary-bg px-3 py-2.5 text-sm text-text" data-testid="missing">
      <span aria-hidden="true">ⓘ</span>
      <div>
        <strong>{{ t('chat.missing_label') }}</strong> {{ turn.missing }}
      </div>
    </div>

    <p v-if="s === 'stopped'" class="mt-2 text-sm text-text-muted">{{ t('chat.stopped') }}</p>
    <div v-if="errorKey" class="mt-2.5 flex items-center gap-2 rounded-md border border-warning bg-warning-bg px-3 py-2.5 text-sm text-text" role="alert">
      <span class="flex-1">{{ t(errorKey) }}</span>
      <Button size="sm" variant="secondary" @click="emit('retry')">{{ t('chat.retry') }}</Button>
    </div>

    <SourceList v-if="showSources" :indices="used" :citations="turn.citations" :gone="gone" @cite="cite" />
  </article>
</template>
