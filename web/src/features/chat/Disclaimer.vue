<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// CMP-08: luôn hiển thị dưới ô nhập, ở mọi trạng thái.
const { t } = useI18n()
</script>

<template>
  <p class="mt-1 text-center text-xs text-text-muted" data-testid="disclaimer">{{ t('chat.disclaimer') }}</p>
</template>
