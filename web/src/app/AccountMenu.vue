<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { CurrentUser } from '@/shared/api'
import { Button, Menu, type MenuItem } from '@/shared/ui'
import { logout } from './session'

const props = defineProps<{ user: CurrentUser }>()
const { t } = useI18n()
const router = useRouter()

// Quản trị/Báo cáo chưa có tính năng (làm sau) nên chưa hiện; logic theo vai trò đã sẵn.
const ADMIN_FEATURES_READY = false
const items = computed<MenuItem[]>(() => [
  { key: 'change-password', label: t('app.change_password') },
  { key: 'help', label: t('app.help') },
  ...(ADMIN_FEATURES_READY && props.user.role === 'admin' ? [{ key: 'admin', label: t('app.admin') }] : []),
  ...(ADMIN_FEATURES_READY && props.user.role !== 'member' ? [{ key: 'reports', label: t('app.reports') }] : []),
  { key: 'logout', label: t('app.logout') },
])

function select(key: string) {
  if (key === 'change-password') void router.push({ name: 'change-password' })
  else if (key === 'help') window.open('#help', '_self')
  else if (key === 'logout') void logout(router)
}
</script>

<template>
  <Menu :items="items" @select="select">
    <template #trigger>
      <Button variant="ghost" size="sm" :aria-label="t('app.account_menu', { name: user.display_name })">
        <span aria-hidden="true" class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary-bg text-xs font-semibold text-primary">
          {{ user.display_name.slice(0, 1).toUpperCase() }}
        </span>
        <span class="max-w-[140px] truncate text-text">{{ user.display_name }}</span>
      </Button>
    </template>
  </Menu>
</template>
