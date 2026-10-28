<!-- Ca vi phạm có chủ đích trong SFC (T01 mục 5); lint.test.ts lint file này tại src/features/chat/Violations.vue. -->
<script setup lang="ts">
import { router } from '@/app/router'
import { } from '@/features/documents/upload'

const html = '<b>x</b>'
const accent = '#b91c1c'
fetch('/api/x')
void router
</script>

<template>
  <div v-html="html" :style="{ color: accent }" />
</template>
