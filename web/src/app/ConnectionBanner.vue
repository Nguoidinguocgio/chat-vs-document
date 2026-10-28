<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

// Banner mất mạng theo navigator.onLine + sự kiện online/offline; ẩn khi có mạng.
const { t } = useI18n()
const offline = ref(!navigator.onLine)
const update = () => (offline.value = !navigator.onLine)

onMounted(() => {
  window.addEventListener('online', update)
  window.addEventListener('offline', update)
})
onBeforeUnmount(() => {
  window.removeEventListener('online', update)
  window.removeEventListener('offline', update)
})
</script>

<template>
  <div v-if="offline" role="status" class="border-b border-warning bg-warning-bg px-4 py-2 text-center text-sm text-text">
    {{ t('network.offline') }}
  </div>
</template>
