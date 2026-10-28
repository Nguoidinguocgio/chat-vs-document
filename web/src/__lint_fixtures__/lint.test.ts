// @vitest-environment node
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { ESLint } from 'eslint'
import { describe, expect, it } from 'vitest'

// Khẳng định từng luật kiến trúc thật sự báo lỗi (không bị tắt nhầm) và các ngoại lệ có chủ đích không báo lỗi.
const webRoot = join(__dirname, '..', '..')
const eslint = new ESLint({ cwd: webRoot })

async function ruleIds(code: string, virtualPath: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: join(webRoot, virtualPath) })
  return result.messages.map((m) => m.ruleId ?? `fatal: ${m.message}`)
}

interface Case {
  name: string
  path: string
  expected: string
  code: string
}

function parseCases(source: string): Case[] {
  return source
    .split(/^\/\/ @case /m)
    .slice(1)
    .map((block) => {
      const [header, ...rest] = block.split('\n')
      const [name, path, expected] = header.trim().split(/\s+/)
      return { name, path, expected, code: rest.join('\n') }
    })
}

const cases = parseCases(readFileSync(join(__dirname, 'violations.ts'), 'utf8'))

describe('luật lint kiến trúc (violations.ts)', () => {
  it('có đủ ca cho R-1…R-6', () => {
    for (const r of ['R-1', 'R-2', 'R-3', 'R-4', 'R-5', 'R-6']) expect(cases.some((c) => c.name.startsWith(r) && c.expected !== 'ok')).toBe(true)
  })

  it.each(cases.map((c) => [c.name, c] as const))('%s', async (_, c) => {
    const ids = await ruleIds(c.code, c.path)
    if (c.expected === 'ok') expect(ids).toEqual([])
    else expect(ids).toContain(c.expected)
  })
})

describe('luật lint kiến trúc (violations.vue)', () => {
  it('báo lỗi R-1/R-2/R-3/R-4 và mã màu cứng trong SFC', async () => {
    const ids = await ruleIds(readFileSync(join(__dirname, 'violations.vue'), 'utf8'), 'src/features/chat/Violations.vue')
    expect(ids).toEqual(
      expect.arrayContaining(['boundaries/element-types', 'boundaries/entry-point', 'no-restricted-globals', 'vue/no-v-html', 'no-restricted-syntax']),
    )
  })
})
