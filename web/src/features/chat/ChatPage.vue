<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useQueryClient } from '@tanstack/vue-query'
import { CONVERSATIONS_KEY } from '@/features/conversations'
import { DOCUMENTS_KEY } from '@/features/documents'
import { Button, EmptyState, Spinner } from '@/shared/ui'
import ChatPane from './ChatPane.vue'
import { useConversationRoute } from './useConversationRoute'
import type { AskHooks } from './useAnswerStream'

// Trang chat cho /c và /c/:id (T15 mục 3.1) + phím tắt `/` và Ctrl+Shift+O (mục 3.5).
const props = defineProps<{ conversationId: string | null }>()
const emit = defineEmits<{ openDocuments: []; openCitation: [ref: { messageId: string; index: number }] }>()
const { t } = useI18n()
const router = useRouter()
const qc = useQueryClient()
const routeId = computed(() => props.conversationId)
const chat = useConversationRoute(routeId)
const pane = ref<InstanceType<typeof ChatPane>>()

const hooks: AskHooks = {
  onCreated: (id) => {
    void router.replace(`/c/${id}`)
    void qc.invalidateQueries({ queryKey: CONVERSATIONS_KEY })
  },
  onNoDocuments: () => void qc.invalidateQueries({ queryKey: DOCUMENTS_KEY }),
  onSettled: () => void qc.invalidateQueries({ queryKey: CONVERSATIONS_KEY }),
}

function inEditable(e: KeyboardEvent): boolean {
  const el = e.target as HTMLElement | null
  return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)
}

function onKey(e: KeyboardEvent) {
  if (e.ctrlKey && e.shiftKey && (e.key === 'O' || e.key === 'o')) {
    e.preventDefault()
    void router.push('/c')
  } else if (e.key === '/' && !e.ctrlKey && !e.metaKey && !e.altKey && !inEditable(e)) {
    e.preventDefault()
    pane.value?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="flex h-full flex-col">
    <div v-if="chat.notFound" class="flex flex-1 items-center justify-center p-8" data-testid="conversation-not-found">
      <EmptyState icon="🔍" :title="t('chat.conversation_not_found')" :description="t('chat.conversation_not_found_text')">
        <Button @click="router.push('/c')">{{ t('chat.back_to_new') }}</Button>
      </EmptyState>
    </div>
    <div v-else-if="chat.loading" class="flex flex-1 items-center justify-center gap-2 text-text-muted" role="status">
      <Spinner />
      <span>{{ t('chat.loading') }}</span>
    </div>
    <ChatPane
      v-else
      ref="pane"
      :draft-key="conversationId ?? 'new'"
      :hooks="hooks"
      @open-documents="emit('openDocuments')"
      @cite="(messageId, index) => emit('openCitation', { messageId, index })"
    />
  </div>
</template>
