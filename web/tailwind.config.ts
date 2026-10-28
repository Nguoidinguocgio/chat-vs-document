import type { Config } from 'tailwindcss'

// Mọi giá trị lấy từ CSS variables trong src/shared/ui/tokens.css (không mã màu cứng ở đây).
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--color-bg)',
        'bg-subtle': 'var(--color-bg-subtle)',
        text: 'var(--color-text)',
        'text-muted': 'var(--color-text-muted)',
        border: 'var(--color-border)',
        'border-strong': 'var(--color-border-strong)',
        primary: 'var(--color-primary)',
        'primary-hover': 'var(--color-primary-hover)',
        'primary-bg': 'var(--color-primary-bg)',
        success: 'var(--color-success)',
        'success-bg': 'var(--color-success-bg)',
        warning: 'var(--color-warning)',
        'warning-bg': 'var(--color-warning-bg)',
        danger: 'var(--color-danger)',
        'danger-bg': 'var(--color-danger-bg)',
        highlight: 'var(--color-highlight)',
      },
      fontFamily: { sans: 'var(--font-sans)' },
      fontSize: {
        xs: ['12px', '16px'],
        sm: ['14px', '20px'],
        base: ['16px', '26px'],
        lg: ['18px', '28px'],
        xl: ['24px', '32px'],
      },
      spacing: {
        1: 'var(--space-1)', 2: 'var(--space-2)', 3: 'var(--space-3)', 4: 'var(--space-4)',
        6: 'var(--space-6)', 8: 'var(--space-8)', 12: 'var(--space-12)',
      },
      borderRadius: { sm: 'var(--radius-sm)', md: 'var(--radius-md)', lg: 'var(--radius-lg)' },
      boxShadow: { e1: 'var(--shadow-e1)', e2: 'var(--shadow-e2)', e3: 'var(--shadow-e3)' },
    },
  },
} satisfies Config
