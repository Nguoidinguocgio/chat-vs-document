import '@testing-library/jest-dom/vitest'
import { config } from '@vue/test-utils'
import { i18n } from '@/shared/lib/i18n'

// Component dùng chung lấy văn bản mặc định từ i18n (T02 mục 3.4)
config.global.plugins = [i18n]
