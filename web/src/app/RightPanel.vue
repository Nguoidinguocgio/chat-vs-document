<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { DocumentsPanel } from '@/features/documents'
import { useLayoutStore } from './layoutStore'

// Khe bảng phải duy nhất (CMP-10): 'documents' (T11) | 'source' (T18). Esc đóng.
const layout = useLayoutStore()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && layout.panel !== 'closed') layout.closePanel()
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <DocumentsPanel v-if="layout.panel === 'documents'" @close="layout.closePanel()" />
</template>
