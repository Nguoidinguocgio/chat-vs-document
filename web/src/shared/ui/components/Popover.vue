<script setup lang="ts">
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'

const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child><slot name="trigger" /></PopoverTrigger>
    <PopoverPortal>
      <PopoverContent :side-offset="4" class="z-50 min-w-[220px] rounded-md border border-border bg-bg p-3 text-sm shadow-e2">
        <slot />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
