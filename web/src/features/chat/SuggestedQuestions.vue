<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// Bấm gợi ý CHỈ điền vào ô nhập, không tự gửi (DD-06).
const emit = defineEmits<{ pick: [question: string] }>()
const { t } = useI18n()
const keys = ['chat.suggest_1', 'chat.suggest_2', 'chat.suggest_3']
</script>

<template>
  <ul class="mt-2 grid w-full gap-2" :aria-label="t('chat.suggestions')">
    <li v-for="k in keys" :key="k">
      <button type="button" class="w-full rounded-md border border-border-strong bg-bg px-3.5 py-2.5 text-left text-sm hover:bg-bg-subtle" @click="emit('pick', t(k))">
        {{ t(k) }}
      </button>
    </li>
  </ul>
</template>
