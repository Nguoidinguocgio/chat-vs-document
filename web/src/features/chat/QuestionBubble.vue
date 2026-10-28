<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{ text: string }>()
const { t } = useI18n()
</script>

<template>
  <div class="max-w-[82%] justify-self-end whitespace-pre-wrap rounded-lg border border-border bg-bg-subtle px-3.5 py-2.5 text-base [overflow-wrap:anywhere]" :aria-label="t('chat.your_question')" role="group">
    {{ text }}
  </div>
</template>
