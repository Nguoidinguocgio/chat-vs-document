<script setup lang="ts">
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'

export interface MenuItem {
  key: string
  label: string
}
defineProps<{ items: MenuItem[] }>()
const emit = defineEmits<{ select: [key: string] }>()
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child><slot name="trigger" /></DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent :side-offset="4" class="min-w-[180px] rounded-md border border-border bg-bg p-1 shadow-e2">
        <DropdownMenuItem
          v-for="item in items"
          :key="item.key"
          class="cursor-pointer rounded-sm px-3 py-2 text-sm outline-none data-[highlighted]:bg-primary-bg"
          @select="emit('select', item.key)"
        >
          {{ item.label }}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
