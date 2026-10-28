import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(join(__dirname, 'tokens.css'), 'utf8')

function token(name: string): string {
  const m = css.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`))
  if (!m) throw new Error(`missing token ${name}`)
  return m[1].toLowerCase()
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe('tokens.css (SDD 3.4)', () => {
  const expected: Record<string, string> = {
    'color-bg': '#ffffff', 'color-bg-subtle': '#f8fafc', 'color-text': '#0f172a', 'color-text-muted': '#475569',
    'color-border': '#cbd5e1', 'color-border-strong': '#64748b', 'color-primary': '#1d4ed8', 'color-primary-hover': '#1e40af',
    'color-success': '#15803d', 'color-warning': '#b45309', 'color-danger': '#b91c1c', 'color-highlight': '#fde68a',
    'color-primary-bg': '#eff6ff', 'color-success-bg': '#f0fdf4', 'color-warning-bg': '#fffbeb', 'color-danger-bg': '#fef2f2',
  }
  it.each(Object.entries(expected))('%s = %s', (name, value) => expect(token(name)).toBe(value))

  it('đặt chuyển động về 0 khi prefers-reduced-motion', () => {
    expect(css).toMatch(/prefers-reduced-motion: reduce[\s\S]*--motion-fast: 0ms/)
  })
})

describe('tương phản (chữ ≥ 4.5:1, thành phần UI ≥ 3:1)', () => {
  const textPairs: [string, string][] = [
    ['color-text', 'color-bg'], ['color-text', 'color-bg-subtle'], ['color-text-muted', 'color-bg'], ['color-text-muted', 'color-bg-subtle'],
    ['color-bg', 'color-primary'], ['color-bg', 'color-primary-hover'], ['color-bg', 'color-danger'], ['color-primary', 'color-bg'],
    ['color-primary', 'color-primary-bg'], ['color-success', 'color-success-bg'], ['color-warning', 'color-warning-bg'],
    ['color-danger', 'color-danger-bg'], ['color-text', 'color-primary-bg'], ['color-text', 'color-warning-bg'], ['color-text', 'color-danger-bg'],
    ['color-primary', 'color-bg-subtle'],
  ]
  it.each(textPairs)('%s trên %s ≥ 4.5', (fg, bg) => expect(contrast(token(fg), token(bg))).toBeGreaterThanOrEqual(4.5))

  it('viền đậm của ô nhập ≥ 3:1 so với nền', () => expect(contrast(token('color-border-strong'), token('color-bg'))).toBeGreaterThanOrEqual(3))
})

describe('không dùng mã màu cứng ngoài tokens.css', () => {
  const root = join(__dirname, '..', '..')
  const hex = /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![\w-])/
  function walk(dir: string): string[] {
    return readdirSync(dir).flatMap((f) => {
      const p = join(dir, f)
      if (f === '__lint_fixtures__') return []
      if (statSync(p).isDirectory()) return walk(p)
      return /\.(vue|ts|css)$/.test(f) ? [p] : []
    })
  }
  it('mọi file nguồn', () => {
    const offenders = walk(root).filter((p) => !p.endsWith('tokens.css') && !p.endsWith('tokens.test.ts') && hex.test(readFileSync(p, 'utf8')))
    expect(offenders).toEqual([])
  })
})
