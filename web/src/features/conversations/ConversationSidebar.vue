<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button, Skeleton } from '@/shared/ui'
import { groupConversations, useConversations } from './useConversations'

// CMP-03 (phạm vi tối thiểu T15): tạo mới + danh sách nhóm theo thời gian; xóa/hết hạn làm sau.
const props = defineProps<{ activeId: string | null }>()
const emit = defineEmits<{ new: []; open: [id: string] }>()
const { t } = useI18n()
const { data, isPending, isError } = useConversations()
const groups = computed(() => groupConversations(data.value ?? []))

// Skeleton chỉ khi tải lâu hơn 300 ms (tránh nháy).
const slow = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
watch(
  isPending,
  (p) => {
    clearTimeout(timer)
    slow.value = false
    if (p) timer = setTimeout(() => (slow.value = true), 300)
  },
  { immediate: true },
)
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="flex h-full flex-col gap-3 overflow-y-auto p-3">
    <Button class="w-full" data-testid="new-conversation" @click="emit('new')">{{ t('conversations.new') }}</Button>
    <div v-if="isPending && slow" class="flex flex-col gap-2" aria-hidden="true">
      <Skeleton v-for="i in 4" :key="i" class="h-8 w-full" />
    </div>
    <p v-else-if="isError" class="px-2 text-sm text-text-muted">{{ t('conversations.load_failed') }}</p>
    <p v-else-if="!isPending && groups.length === 0" class="px-2 py-4 text-sm text-text-muted">{{ t('conversations.empty') }}</p>
    <div v-for="g in groups" :key="g.group" class="flex flex-col gap-1">
      <h3 class="px-2 pt-2 text-xs font-semibold uppercase text-text-muted">{{ t(`conversations.${g.group}`) }}</h3>
      <ul class="flex flex-col gap-1">
        <li v-for="c in g.items" :key="c.id">
          <a
            :href="`/c/${c.id}`"
            class="block truncate rounded-md px-2 py-2 text-sm text-text hover:bg-border"
            :class="{ 'bg-primary-bg font-medium text-primary': c.id === props.activeId }"
            :aria-current="c.id === props.activeId ? 'true' : undefined"
            :title="c.title"
            @click.prevent="emit('open', c.id)"
            >{{ c.title }}</a
          >
        </li>
      </ul>
    </div>
  </div>
</template>
