<script setup lang="ts">
withDefaults(defineProps<{ width?: string; height?: string }>(), { width: '100%', height: '16px' })
</script>

<template>
  <div aria-hidden="true" class="animate-pulse rounded-md bg-border motion-reduce:animate-none" :style="{ width, height }" />
</template>
