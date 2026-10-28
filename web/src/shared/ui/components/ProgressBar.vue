<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: number; label: string }>()
const pct = computed(() => Math.min(100, Math.max(0, props.value)))
</script>

<template>
  <div role="progressbar" :aria-label="label" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="pct" class="h-2 w-full overflow-hidden rounded-sm bg-border">
    <div class="h-full bg-primary transition-[width] duration-[var(--motion-base)]" :style="{ width: pct + '%' }" />
  </div>
</template>
