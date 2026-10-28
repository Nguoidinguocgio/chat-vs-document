import { createI18n } from 'vue-i18n'
import vi from '@/locales/vi'

export const i18n = createI18n({
  legacy: false,
  locale: 'vi',
  fallbackLocale: 'vi',
  messages: { vi },
  missingWarn: import.meta.env.DEV,
  fallbackWarn: false,
})

/** Dịch ngoài component (ví dụ trong toast của lớp API). */
export function t(key: string, params?: Record<string, unknown>): string {
  return params ? i18n.global.t(key, params) : i18n.global.t(key)
}

/** Khóa có tồn tại không (thông điệp lỗi máy chủ chỉ hiển thị khi có khóa tương ứng). */
export function hasMessage(key: string): boolean {
  return i18n.global.te(key)
}
