// API công khai của feature auth (T05). Feature khác chỉ được import từ file này (luật R-2).
export { default as LoginPage } from './LoginPage.vue'
export { default as ChangePasswordPage } from './ChangePasswordPage.vue'
export { authApi, fetchCurrentUser, ME_KEY, passwordRules, safeNext, useCurrentUser } from './api'
