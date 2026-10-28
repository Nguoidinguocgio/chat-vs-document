<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { CHIP_CLASS, decorateCitations, placeCursor, type ChipInfo } from './citationPlugin'
import { renderBlock, splitBlocks } from './sanitizeConfig'

// Câu trả lời Markdown của LLM (không tin cậy) → HTML đã làm sạch. Mỗi khối một phần tử: khi stream
// chỉ khối cuối đổi chuỗi HTML nên Vue chỉ gán lại innerHTML của khối đó.
const props = defineProps<{ source: string; chips: ReadonlyMap<number, ChipInfo>; streaming?: boolean }>()
const emit = defineEmits<{ cite: [index: number] }>()
const root = ref<HTMLElement>()
const blocks = computed(() => splitBlocks(props.source).map(renderBlock))

// Chỉ trang trí lại khối có HTML hoặc bảng chip đổi (khi stream: thường chỉ khối cuối).
const decorated = new WeakMap<Element, { html: string; chips: ReadonlyMap<number, ChipInfo> }>()

function decorate() {
  if (!root.value) return
  const els = Array.from(root.value.children) as HTMLElement[]
  els.forEach((el, i) => {
    const prev = decorated.get(el)
    if (prev && prev.html === blocks.value[i] && prev.chips === props.chips) return
    decorateCitations(el, props.chips)
    decorated.set(el, { html: blocks.value[i], chips: props.chips })
  })
  placeCursor(root.value, !!props.streaming)
}

watch([blocks, () => props.chips, () => props.streaming], () => void nextTick(decorate), { flush: 'post' })
onMounted(decorate)

function onClick(e: MouseEvent) {
  const b = (e.target as HTMLElement).closest<HTMLButtonElement>(`button.${CHIP_CLASS}`)
  if (b?.dataset.cite) emit('cite', Number(b.dataset.cite))
}
</script>

<template>
  <!-- R-4: ngoại lệ duy nhất được dùng v-html — HTML đã qua DOMPurify với danh sách trắng. -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div ref="root" class="answer-md" @click="onClick"><div v-for="(html, i) in blocks" :key="i" v-html="html" /></div>
</template>

<style scoped>
.answer-md {
  font-size: 16px;
  line-height: 26px;
  overflow-wrap: anywhere;
}
.answer-md :deep(p),
.answer-md :deep(ul),
.answer-md :deep(ol),
.answer-md :deep(blockquote),
.answer-md :deep(pre),
.answer-md :deep(table) {
  margin: 0 0 10px;
}
.answer-md :deep(ul) {
  list-style: disc;
  padding-left: 22px;
}
.answer-md :deep(ol) {
  list-style: decimal;
  padding-left: 22px;
}
.answer-md :deep(h3),
.answer-md :deep(h4) {
  margin: 12px 0 6px;
  font-weight: 600;
}
.answer-md :deep(blockquote) {
  border-left: 3px solid var(--color-border);
  padding-left: 12px;
  color: var(--color-text-muted);
}
.answer-md :deep(code) {
  border-radius: var(--radius-sm);
  background: var(--color-bg-subtle);
  padding: 0 4px;
  font-size: 14px;
}
.answer-md :deep(pre) {
  overflow-x: auto;
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  padding: 8px 12px;
}
.answer-md :deep(th),
.answer-md :deep(td) {
  border: 1px solid var(--color-border);
  padding: 4px 8px;
}
.answer-md :deep(.md-cite) {
  display: inline-grid;
  place-items: center;
  min-width: 24px;
  height: 24px;
  margin: 0 2px;
  padding: 0 4px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  vertical-align: baseline;
}
.answer-md :deep(.md-cite.gone) {
  border-color: var(--color-border-strong);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
  text-decoration: line-through;
}
.answer-md :deep(.md-cursor)::after {
  content: '▍';
  color: var(--color-primary);
  animation: md-blink 1s steps(2) infinite;
}
@keyframes md-blink {
  50% {
    opacity: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .answer-md :deep(.md-cursor)::after {
    animation: none;
  }
}
</style>
