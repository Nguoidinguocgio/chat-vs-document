<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useQueryClient } from '@tanstack/vue-query'
import { isApiError } from '@/shared/api'
import { Alert, Button, Input } from '@/shared/ui'
import PasswordInput from './PasswordInput.vue'
import { authApi, ME_KEY, safeNext } from './api'

// SCR-01 Đăng nhập (T05 mục 3.4).
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const qc = useQueryClient()

const email = ref('')
const password = ref('')
const submitted = ref(false)
const sending = ref(false)
const notice = ref<{ tone: 'info' | 'warning' | 'error'; text: string } | null>(null)
const emailInput = ref<InstanceType<typeof Input>>()
const passwordInput = ref<InstanceType<typeof PasswordInput>>()

const reason = route.query.reason
if (reason === 'expired') notice.value = { tone: 'info', text: t('auth.session_expired') }
else if (reason === 'revoked') notice.value = { tone: 'error', text: t('auth.disabled') }

const emailError = computed(() => {
  if (!submitted.value) return undefined
  const v = email.value.trim()
  if (!v) return t('auth.email_required')
  return /^\S+@\S+\.\S+$/.test(v) ? undefined : t('auth.email_invalid')
})
const passwordError = computed(() => (submitted.value && !password.value ? t('auth.password_required') : undefined))

async function submit() {
  submitted.value = true
  if (emailError.value) return emailInput.value?.focus()
  if (passwordError.value) return passwordInput.value?.focus()
  sending.value = true
  notice.value = null
  try {
    const user = await authApi.login(email.value.trim(), password.value)
    qc.setQueryData(ME_KEY, user)
    await router.replace(user.must_change_password ? '/change-password?mode=required' : safeNext(route.query.next))
  } catch (e) {
    password.value = ''
    if (isApiError(e, 'ACCOUNT_LOCKED')) {
      const s = Number(e.details.retry_after_s) || 900
      notice.value = { tone: 'warning', text: t('auth.locked', { minutes: Math.max(1, Math.ceil(s / 60)) }) }
    } else if (isApiError(e, 'ACCOUNT_DISABLED')) {
      notice.value = { tone: 'error', text: t('auth.disabled') }
    } else if (isApiError(e, 'INVALID_CREDENTIALS')) {
      // Cùng một thông điệp cho sai email và sai mật khẩu (AC-001.2).
      notice.value = { tone: 'error', text: t('auth.invalid_credentials') }
    } else {
      notice.value = { tone: 'error', text: t('errors.generic') }
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-bg-subtle p-4">
    <div class="w-full max-w-[400px] rounded-lg border border-border bg-bg p-8 shadow-e1">
      <div class="mb-6 flex flex-col items-center gap-2 text-center">
        <svg width="44" height="44" viewBox="0 0 28 28" aria-hidden="true" class="text-primary">
          <rect width="28" height="28" rx="7" fill="currentColor" />
          <path d="M8 7h8l4 4v10H8z" class="fill-bg" />
        </svg>
        <h1 class="text-xl font-semibold">{{ t('auth.login_title') }}</h1>
        <p class="text-sm text-text-muted">{{ t('auth.login_subtitle') }}</p>
      </div>
      <Alert v-if="notice" :tone="notice.tone" class="mb-4" data-testid="login-notice">{{ notice.text }}</Alert>
      <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
        <Input
          ref="emailInput"
          v-model="email"
          type="email"
          autocomplete="username"
          :label="t('auth.email')"
          :placeholder="t('auth.email_placeholder', { at: '@' })"
          :error="emailError"
        />
        <PasswordInput ref="passwordInput" v-model="password" :label="t('auth.password')" autocomplete="current-password" :error="passwordError" />
        <Button type="submit" :loading="sending">{{ sending ? t('auth.logging_in') : t('auth.login') }}</Button>
      </form>
      <p class="mt-6 text-center text-xs text-text-muted">{{ t('auth.login_foot') }}</p>
    </div>
  </main>
</template>
