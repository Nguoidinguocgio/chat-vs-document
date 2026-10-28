<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import Composer from './Composer.vue'
import MessageList from './MessageList.vue'
import { useAnswerStream, type AskHooks } from './useAnswerStream'

// Khung chat: danh sách tin nhắn + ô nhập + vùng aria-live riêng cho thông báo bắt đầu/hoàn tất.
const props = defineProps<{ draftKey: string; hooks: AskHooks }>()
const emit = defineEmits<{ openDocuments: []; cite: [messageId: string, index: number] }>()
const chat = useAnswerStream()
const { turns, busy, notice, announcement } = storeToRefs(chat)
const question = ref('')
const composer = ref<InstanceType<typeof Composer>>()

function pick(q: string) {
  question.value = q
  composer.value?.focus()
}

defineExpose({ focus: () => composer.value?.focus() })
</script>

<template>
  <section class="flex h-full min-h-0 flex-col">
    <MessageList :turns="turns" @pick="pick" @open-documents="emit('openDocuments')" @cite="(m, i) => emit('cite', m, i)" @retry="chat.retry($event, props.hooks)" />
    <Composer
      ref="composer"
      v-model="question"
      :draft-key="draftKey"
      :busy="busy"
      :notice="notice"
      @submit="chat.ask($event, props.hooks)"
      @stop="chat.abort('stop')"
      @open-documents="emit('openDocuments')"
    />
    <div class="sr-only-text" aria-live="polite" data-testid="chat-announcer">{{ announcement }}</div>
  </section>
</template>
