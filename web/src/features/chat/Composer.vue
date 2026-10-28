<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCurrentUser } from '@/features/auth'
import { requestFilePicker, useDocuments, useDocumentsSummary } from '@/features/documents'
import { clearDraft, flushDrafts, loadDraft, saveDraft } from '@/shared/lib/draft'
import Disclaimer from './Disclaimer.vue'
import { COUNTER_FROM, MAX_QUESTION, useComposerState } from './useComposerState'

// CMP-07: Enter gửi, Shift+Enter xuống dòng, bỏ qua Enter khi đang soạn dấu bằng bộ gõ (IME).
const props = defineProps<{ draftKey: string; busy: boolean; notice?: string | null }>()
const emit = defineEmits<{ submit: [question: string]; stop: []; openDocuments: [] }>()
const text = defineModel<string>({ default: '' })
const { t } = useI18n()
const { data: me } = useCurrentUser()
const summary = useDocumentsSummary()
const { isSuccess: documentsLoaded } = useDocuments()
const state = useComposerState(text, computed(() => props.busy), summary, documentsLoaded)
const el = ref<HTMLTextAreaElement>()
const composing = ref(false)

// Nạp bản nháp khi vào hội thoại / đăng nhập lại (AC-001.5).
watch(
  () => [me.value?.id, props.draftKey] as const,
  ([userId, key]) => {
    if (userId) text.value = loadDraft(userId, key)
  },
  { immediate: true },
)
watch(text, (v) => {
  if (me.value) saveDraft(me.value.id, props.draftKey, v)
  void nextTick(autosize)
})
// Câu trả lời kết thúc → focus lại ô nhập.
watch(
  () => props.busy,
  (b, was) => {
    if (was && !b) void nextTick(() => el.value?.focus())
  },
)
onBeforeUnmount(flushDrafts)

function autosize() {
  const ta = el.value
  if (!ta) return
  ta.style.height = 'auto'
  ta.style.height = `${Math.min(ta.scrollHeight, 8 * 24 + 12)}px`
}

function send() {
  if (!state.value.canSend) return
  const q = text.value
  text.value = ''
  if (me.value) clearDraft(me.value.id, props.draftKey)
  emit('submit', q)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter' || e.shiftKey) return
  if (e.isComposing || composing.value || e.keyCode === 229) return
  e.preventDefault()
  send()
}

function onButton() {
  if (props.busy) emit('stop')
  else send()
}

function upload() {
  emit('openDocuments')
  requestFilePicker()
}

const len = computed(() => text.value.length)
defineExpose({ focus: () => el.value?.focus() })
</script>

<template>
  <div class="border-t border-border bg-bg px-4 pb-2 pt-3">
    <div class="mx-auto max-w-[760px]">
      <div
        class="flex items-end gap-2 rounded-lg border border-border-strong p-2 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary"
        :class="state.locked ? 'bg-bg-subtle' : 'bg-bg'"
      >
        <textarea
          id="composer"
          ref="el"
          v-model="text"
          rows="1"
          :disabled="state.locked"
          :placeholder="state.locked ? t('chat.locked_placeholder') : t('chat.question_placeholder')"
          :aria-label="t('chat.question_label')"
          aria-describedby="composer-status"
          class="max-h-[204px] flex-1 resize-none border-0 bg-transparent p-1.5 text-base leading-6 outline-none disabled:cursor-not-allowed"
          @keydown="onKeydown"
          @compositionstart="composing = true"
          @compositionend="composing = false"
        />
        <button
          type="button"
          data-testid="send"
          class="touch-target inline-flex h-10 w-10 flex-none items-center justify-center rounded-md text-lg disabled:cursor-not-allowed"
          :class="busy ? 'border border-border-strong bg-bg text-text' : 'bg-primary text-bg hover:bg-primary-hover disabled:bg-border-strong'"
          :aria-label="busy ? t('chat.stop') : t('chat.send')"
          :disabled="!busy && !state.canSend"
          @click="onButton"
        >
          <span aria-hidden="true">{{ busy ? '■' : '➤' }}</span>
        </button>
      </div>
      <div id="composer-status" class="mt-1.5 flex min-h-[18px] justify-between gap-3 text-[13px] text-text-muted" data-testid="composer-status" :data-state="state.code">
        <span :class="{ 'text-danger': state.code === 'too_long' }">
          <template v-if="state.code === 'offline'">{{ t('chat.offline') }}</template>
          <template v-else-if="state.code === 'no_documents'">
            {{ t('chat.empty_workspace') }}
            <button type="button" class="text-primary underline" @click="upload">{{ t('documents.upload') }}</button>
          </template>
          <template v-else-if="state.code === 'processing_only'">{{ t('chat.all_processing', { n: summary.processing }) }}</template>
          <template v-else-if="state.code === 'all_failed'">
            {{ t('chat.all_failed_title') }}
            <button type="button" class="text-primary underline" @click="emit('openDocuments')">{{ t('chat.open_documents') }}</button>
          </template>
          <template v-else-if="state.code === 'too_long'">{{ t('chat.too_long') }}</template>
          <template v-else-if="notice">{{ t(notice) }}</template>
        </span>
        <span v-if="len >= COUNTER_FROM" data-testid="char-count" :class="{ 'font-semibold text-danger': len > MAX_QUESTION }">{{ t('chat.char_count', { n: len }) }}</span>
      </div>
      <Disclaimer />
    </div>
  </div>
</template>
