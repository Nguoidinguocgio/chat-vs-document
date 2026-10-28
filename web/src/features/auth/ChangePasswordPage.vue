<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useQueryClient } from '@tanstack/vue-query'
import { isApiError, type CurrentUser } from '@/shared/api'
import { Alert, Button } from '@/shared/ui'
import PasswordInput from './PasswordInput.vue'
import { authApi, ME_KEY, passwordRules, useCurrentUser } from './api'

// SCR-02 Đổi mật khẩu: chế độ `required` (mật khẩu tạm, không có mật khẩu hiện tại, không có Hủy) và `change`.
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const qc = useQueryClient()
const { data: me } = useCurrentUser()

const required = computed(() => route.query.mode === 'required' || !!me.value?.must_change_password)
const current = ref('')
const next = ref('')
const confirm = ref('')
const submitted = ref(false)
const saving = ref(false)
const done = ref(false)
const serverError = ref<string | null>(null)
const rulesId = useId()

const rules = computed(() => passwordRules(next.value))
const rulesOk = computed(() => rules.value.length && rules.value.letter && rules.value.digit)
const currentError = computed(() => (submitted.value && !required.value && !current.value ? t('auth.current_required') : undefined))
const nextError = computed(() => (submitted.value && !rulesOk.value ? t('auth.password_policy') : undefined))
const confirmError = computed(() => (submitted.value && confirm.value !== next.value ? t('auth.mismatch') : undefined))

const ruleList = computed(() => [
  { key: 'length', ok: rules.value.length, label: t('auth.rule_length') },
  { key: 'letter', ok: rules.value.letter, label: t('auth.rule_letter') },
  { key: 'digit', ok: rules.value.digit, label: t('auth.rule_digit') },
])

async function submit() {
  submitted.value = true
  serverError.value = null
  if (currentError.value || nextError.value || confirmError.value) return
  saving.value = true
  try {
    await authApi.changePassword(next.value, required.value ? null : current.value)
    qc.setQueryData<CurrentUser>(ME_KEY, (u) => (u ? { ...u, must_change_password: false } : u))
    done.value = true
    await router.replace('/c')
  } catch (e) {
    if (isApiError(e, 'INVALID_CREDENTIALS')) serverError.value = t('auth.current_wrong')
    else if (isApiError(e, 'VALIDATION_ERROR')) serverError.value = t('auth.password_policy')
    else serverError.value = t('errors.generic')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-bg-subtle p-4">
    <div class="w-full max-w-[440px] rounded-lg border border-border bg-bg p-8 shadow-e1">
      <div v-if="done" role="status" class="text-center">
        <h1 class="text-xl font-semibold">{{ t('auth.changed') }}</h1>
      </div>
      <template v-else>
        <h1 class="text-xl font-semibold">{{ t('auth.change_title') }}</h1>
        <p v-if="me" class="mt-1 text-sm text-text-muted">{{ t('auth.change_subtitle', { email: me.email }) }}</p>
        <Alert v-if="required" class="mt-4" data-testid="required-notice">{{ t('auth.required_notice') }}</Alert>
        <Alert v-if="serverError" tone="error" class="mt-4">{{ serverError }}</Alert>
        <form class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="submit">
          <PasswordInput v-if="!required" v-model="current" :label="t('auth.current_password')" autocomplete="current-password" :error="currentError" />
          <div class="flex flex-col gap-2">
            <PasswordInput v-model="next" :label="t('auth.new_password')" autocomplete="new-password" :error="nextError" :describedby="rulesId" />
            <ul :id="rulesId" :aria-label="t('auth.rules_label')" class="flex flex-col gap-1 text-xs">
              <li v-for="r in ruleList" :key="r.key" :data-rule="r.key" :data-ok="r.ok" class="flex items-center gap-2" :class="r.ok ? 'text-success' : 'text-text-muted'">
                <span aria-hidden="true">{{ r.ok ? '✓' : '○' }}</span>
                <span>{{ r.label }}</span>
                <span class="sr-only-text">({{ r.ok ? t('auth.rule_met') : t('auth.rule_unmet') }})</span>
              </li>
            </ul>
          </div>
          <PasswordInput v-model="confirm" :label="t('auth.confirm_password')" autocomplete="new-password" :error="confirmError" />
          <div class="flex justify-end gap-2">
            <Button v-if="!required" variant="secondary" @click="router.back()">{{ t('ui.cancel') }}</Button>
            <Button type="submit" :loading="saving">{{ saving ? t('auth.saving') : t('auth.save') }}</Button>
          </div>
        </form>
      </template>
    </div>
  </main>
</template>
