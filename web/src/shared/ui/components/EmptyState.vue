<script setup lang="ts">
defineProps<{ title: string; description?: string; icon?: string }>()
</script>

<template>
  <div class="flex flex-col items-center gap-2 rounded-lg border border-dashed border-border p-8 text-center">
    <div class="text-xl" aria-hidden="true">{{ icon ?? '📄' }}</div>
    <h3 class="text-base font-semibold">{{ title }}</h3>
    <p v-if="description" class="max-w-sm text-sm text-text-muted">{{ description }}</p>
    <div v-if="$slots.default" class="mt-2"><slot /></div>
  </div>
</template>
