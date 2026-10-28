<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EmptyWorkspaceState, useDocumentsSummary } from '@/features/documents'
import { Button } from '@/shared/ui'
import type { Turn } from './answerReducer'
import AnswerCard from './AnswerCard.vue'
import QuestionBubble from './QuestionBubble.vue'
import SuggestedQuestions from './SuggestedQuestions.vue'

// CMP-04: role="log" nhưng không aria-live (thông báo hoàn tất qua vùng live riêng của ChatPane).
// Tự cuộn xuống khi có nội dung mới, trừ khi người dùng đã cuộn lên (> 60 px tính từ đáy).
const props = defineProps<{ turns: Turn[] }>()
const emit = defineEmits<{ pick: [question: string]; openDocuments: []; cite: [messageId: string, index: number]; retry: [key: string] }>()
const { t } = useI18n()
const summary = useDocumentsSummary()
const box = ref<HTMLElement>()
const stick = ref(true)
const showJump = ref(false)

function atBottom(): boolean {
  const e = box.value
  return !e || e.scrollHeight - e.scrollTop - e.clientHeight <= 60
}
function onScroll() {
  stick.value = atBottom()
  if (stick.value) showJump.value = false
}
function toBottom() {
  if (box.value) box.value.scrollTop = box.value.scrollHeight
}
function jump() {
  stick.value = true
  showJump.value = false
  toBottom()
}

watch(
  () => [props.turns.length, props.turns.at(-1)?.answer.length, props.turns.at(-1)?.state],
  ([len], old) => {
    // Câu hỏi mới của chính người dùng luôn kéo xuống.
    if (old && len !== old[0]) stick.value = true
    void nextTick(() => {
      if (stick.value) toBottom()
      else showJump.value = true
    })
  },
)
defineExpose({ jump, stick, showJump })
</script>

<template>
  <div class="relative min-h-0 flex-1">
    <div ref="box" class="h-full overflow-y-auto px-4 py-6" role="log" :aria-label="t('chat.log_label')" aria-live="off" data-testid="message-list" @scroll="onScroll">
      <div class="mx-auto grid max-w-[760px] gap-5">
        <template v-if="turns.length === 0">
          <EmptyWorkspaceState v-if="!summary.hasReady" @open-documents="emit('openDocuments')" />
          <div v-else class="mx-auto mt-[6vh] grid w-full max-w-[520px] justify-items-center gap-3 text-center" data-testid="chat-greeting">
            <div class="grid h-24 w-24 place-items-center rounded-[24px] bg-primary-bg text-[44px]" aria-hidden="true">💬</div>
            <h2 class="text-lg font-semibold">{{ t('chat.ready_title') }}</h2>
            <p class="text-text-muted">{{ t('chat.ready_text', { n: summary.ready }) }}</p>
            <SuggestedQuestions @pick="emit('pick', $event)" />
          </div>
        </template>
        <div v-for="turn in turns" :key="turn.key" class="grid gap-5">
          <QuestionBubble :text="turn.question" />
          <AnswerCard :turn="turn" @cite="(m, i) => emit('cite', m, i)" @retry="emit('retry', turn.key)" />
        </div>
      </div>
    </div>
    <Button v-if="showJump" size="sm" variant="secondary" class="absolute bottom-4 left-1/2 -translate-x-1/2 shadow-e2" data-testid="jump-new" @click="jump">
      {{ t('chat.jump_new') }}
    </Button>
  </div>
</template>
