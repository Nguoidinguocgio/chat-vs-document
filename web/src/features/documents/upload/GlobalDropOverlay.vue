<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUpload } from './useUpload'

// Thả file PDF bất kỳ đâu trên màn hình (T10 mục 3.2). Chỉ hiện khi kéo FILE (không phải văn bản/ảnh trong trang);
// bộ đếm dragenter/leave để không nhấp nháy khi đi qua phần tử con.
const emit = defineEmits<{ dropped: [] }>()
const { t } = useI18n()
const { addFiles } = useUpload()
const visible = ref(false)
let depth = 0

const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types ?? []).includes('Files')

function onEnter(e: DragEvent) {
  if (!hasFiles(e)) return
  depth++
  visible.value = true
}
function onLeave(e: DragEvent) {
  if (!hasFiles(e)) return
  depth = Math.max(0, depth - 1)
  if (depth === 0) visible.value = false
}
function onOver(e: DragEvent) {
  if (hasFiles(e)) e.preventDefault()
}
async function onDrop(e: DragEvent) {
  if (!hasFiles(e)) return
  e.preventDefault()
  depth = 0
  visible.value = false
  emit('dropped')
  await addFiles(e.dataTransfer?.files)
}

onMounted(() => {
  window.addEventListener('dragenter', onEnter)
  window.addEventListener('dragleave', onLeave)
  window.addEventListener('dragover', onOver)
  window.addEventListener('drop', onDrop)
})
onBeforeUnmount(() => {
  window.removeEventListener('dragenter', onEnter)
  window.removeEventListener('dragleave', onLeave)
  window.removeEventListener('dragover', onOver)
  window.removeEventListener('drop', onDrop)
})
</script>

<template>
  <div v-if="visible" aria-hidden="true" data-testid="drop-overlay" class="drop-overlay fixed inset-0 z-[95] grid place-items-center text-xl font-semibold text-bg">
    {{ t('documents.drop_overlay') }}
  </div>
</template>

<style scoped>
.drop-overlay {
  background: color-mix(in srgb, var(--color-primary) 88%, transparent);
  pointer-events: none;
}
</style>
