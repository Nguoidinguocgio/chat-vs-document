import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'
import { saveVideo } from '../../helpers/report'
import { stepRunner, overall, writeResult } from '../../helpers/steps'

// TC-AI-04 — TH-07: câu trả lời bịa đặt ≤ 5% (DEP-002). Chế độ `ai`: backend thật + bge-m3 (Ollama) + LLM thật.
// Spec này chạy các lệnh của mục 4 và kiểm tra điều kiện tiên quyết bằng lệnh thật; đầu ra hiển thị trên một trang
// "terminal" để ảnh chụp/video ghi lại đúng những gì đã chạy. Thiếu điều kiện → Bị chặn (README §7), không đánh Đạt.
test.setTimeout(120_000)

const ROOT = resolve(__dirname, '../../..')
const MOCK_PW = 'Matkhau-2026' // mật khẩu tài khoản kiểm thử (được phép xuất hiện — README §7)

type Line = { cmd: string; out: string; ok: boolean }

function sh(cmd: string, args: string[]): { out: string; code: number } {
  const r = spawnSync(cmd, args, { cwd: ROOT, encoding: 'utf8', timeout: 20_000, shell: false })
  if (r.error) return { out: String(r.error.message), code: -1 }
  return { out: `${r.stdout ?? ''}${r.stderr ?? ''}`.trim(), code: r.status ?? -1 }
}

async function http(url: string): Promise<string> {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(3000) })
    return `HTTP ${res.status} ${(await res.text()).slice(0, 160)}`
  } catch (e) {
    const cause = (e as { cause?: { code?: string } }).cause?.code
    return `LỖI: ${cause ?? (e as Error).message}`
  }
}

function envValue(key: string): string | undefined {
  const file = resolve(ROOT, '.env')
  if (!existsSync(file)) return undefined
  const m = readFileSync(file, 'utf8').match(new RegExp(`^\\s*${key}\\s*=(.*)$`, 'm'))
  return m?.[1].trim()
}

test('TC-AI-04 TH-07 câu trả lời bịa đặt', async ({ page, browserName }, testInfo) => {
  const { steps, run } = stepRunner(page)
  const lines: Line[] = []
  const blockers: string[] = []

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.setContent(
    `<html lang="vi"><body style="margin:0;background:#0c0c0c;color:#d4d4d4;font:14px/1.5 Consolas,monospace">
     <div style="background:#1f1f1f;color:#fff;padding:8px 14px;font-weight:bold">TC-AI-04 — TH-07 (DEP-002) · ${ROOT}</div>
     <pre id="t" style="margin:0;padding:12px 16px;white-space:pre-wrap"></pre><div id="s"></div></body></html>`,
  )
  const render = async (banner?: string) => {
    await page.evaluate(
      ({ lines, banner }) => {
        const t = document.getElementById('t')!
        t.innerHTML = ''
        for (const l of lines) {
          const c = document.createElement('div')
          c.style.color = '#4fc1ff'
          c.textContent = `› ${l.cmd}`
          const o = document.createElement('div')
          o.style.cssText = `color:${l.ok ? '#b5cea8' : '#f48771'};margin:0 0 10px 0`
          o.textContent = l.out || '(không có đầu ra)'
          t.append(c, o)
        }
        const s = document.getElementById('s')!
        s.innerHTML = ''
        if (banner) {
          const b = document.createElement('div')
          b.style.cssText = 'position:fixed;left:0;right:0;bottom:0;background:#5a1d1d;color:#fff;padding:10px 16px;font-weight:bold;white-space:pre-wrap'
          b.textContent = banner
          s.append(b)
        }
        window.scrollTo(0, document.body.scrollHeight)
      },
      { lines, banner },
    )
    await page.waitForTimeout(600) // để video kịp ghi từng dòng
  }
  const add = async (cmd: string, out: string, ok: boolean) => {
    lines.push({ cmd, out, ok })
    await render()
  }

  try {
    await run('run-all', async () => {
      // Điều kiện tiên quyết (mục 2)
      const evalFiles = ['eval', 'eval/run.py', 'eval/score.py', 'eval/dep002.yaml', 'eval/corpus']
      const missing = evalFiles.filter((f) => !existsSync(resolve(ROOT, f)))
      await add('[kiểm tra tồn tại] eval/, eval/run.py, eval/score.py, eval/dep002.yaml, eval/corpus', evalFiles.map((f) => `${f}: ${missing.includes(f) ? 'False' : 'True'}`).join('\n'), missing.length === 0)
      if (missing.length) blockers.push(`Thiếu bộ đánh giá DEP-002 (T20 chưa làm): ${missing.join(', ')}`)

      const docker = sh('docker', ['info', '--format', '{{.ServerVersion}}'])
      await add('$ docker info --format {{.ServerVersion}}', docker.out.split('\n').slice(0, 2).join('\n'), docker.code === 0)
      if (docker.code !== 0) blockers.push('Docker Engine không chạy → không có PostgreSQL cho backend thật')

      const api = await http('http://localhost:8080/healthz')
      await add('[HTTP GET] http://localhost:8080/healthz', api, api.startsWith('HTTP 200'))
      if (!api.startsWith('HTTP 200')) blockers.push('Backend thật (localhost:8080) không chạy')

      const ollama = await http('http://localhost:11434/api/tags')
      const hasBge = ollama.includes('bge-m3')
      await add('[HTTP GET] http://localhost:11434/api/tags', ollama, hasBge)
      if (!hasBge) blockers.push('Ollama + bge-m3 không sẵn sàng tại localhost:11434')

      const emb = envValue('EMBEDDING_PROVIDER')
      const llm = envValue('LLM_PROVIDER')
      const key = envValue('LLM_API_KEY')
      const envOk = emb === 'ollama' && llm === 'anthropic' && !!key
      await add('[đọc .env] EMBEDDING_PROVIDER, LLM_PROVIDER, LLM_API_KEY (giá trị khóa được che)', `EMBEDDING_PROVIDER=${emb}\nLLM_PROVIDER=${llm}\nLLM_API_KEY=${key ? '<đã đặt — che>' : '<trống>'}`, envOk)
      if (llm !== 'anthropic') blockers.push(`.env đang LLM_PROVIDER=${llm} (cần anthropic + khóa kiểm thử)`)

      // Lệnh bước 1
      const r = sh('python', ['eval/run.py', '--base-url', 'http://localhost:8080', '--user', 'lan@congty.vn', '--password', MOCK_PW])
      await add(`$ python eval/run.py --base-url http://localhost:8080 --user lan@congty.vn --password ${MOCK_PW}`, `${r.out}\n(exit ${r.code})`, r.code === 0)
      const grading = existsSync(resolve(ROOT, 'grading.csv')) || existsSync(resolve(ROOT, 'eval/grading.csv'))
      await add('[kiểm tra tồn tại] grading.csv, eval/grading.csv', String(grading), grading)
      await render(`BỊ CHẶN — không sinh được grading.csv:\n• ${blockers.join('\n• ')}`)
      expect(r.code, 'eval/run.py phải chạy thành công và sinh grading.csv').toBe(0)
      return 'grading.csv đã sinh'
    }, async () => `BỊ CHẶN: ${blockers.join('; ')}. Lệnh eval/run.py không chạy được (xem ảnh).`)

    await run('grading-done', async () => {
      throw new Error('no grading')
    }, async () => {
      await render('BỊ CHẶN — Bước 2: không có grading.csv để chuyên gia điền cột fabricated (0/1); bộ DEP-002 chưa được Đội PTKD ký (G-03).')
      return 'BỊ CHẶN: không có grading.csv (bước 1 bị chặn); bộ câu hỏi DEP-002 chưa có / chưa được Đội PTKD ký (G-03); chưa có chuyên gia chấm.'
    })

    await run('th07-result', async () => {
      const r = sh('python', ['eval/score.py', 'grading.csv'])
      await add('$ python eval/score.py grading.csv', `${r.out}\n(exit ${r.code})`, r.code === 0)
      expect(r.code).toBe(0)
      return r.out
    }, async () => {
      await render('BỊ CHẶN — Bước 3: eval/score.py và grading.csv không tồn tại → không tính được TH-07.')
      return 'BỊ CHẶN: eval/score.py và grading.csv không tồn tại → không tính được tỷ lệ TH-07.'
    })

    await run('fabrication-analysis', async () => {
      throw new Error('no data')
    }, async () => {
      await render('BỊ CHẶN — Bước 4: không có câu nào được chấm → không có dữ liệu để phân tích nguyên nhân bịa đặt.')
      return 'BỊ CHẶN: không có câu được chấm "bịa" để phân tích (phụ thuộc bước 1–3).'
    })
  } finally {
    const video = await saveVideo(page)
    writeResult('TC-AI-04', {
      status: overall(steps, 4),
      mode: 'ai',
      browser: `${browserName} (${testInfo.project.name}) — trang hiển thị đầu ra lệnh`,
      th07: null,
      config: {
        prompt_version: 'không xác định (chưa chạy)',
        RETRIEVAL_TOP_K: envValue('RETRIEVAL_TOP_K') ?? null,
        RETRIEVAL_MIN_SCORE: envValue('RETRIEVAL_MIN_SCORE') ?? null,
        LLM_MODEL: envValue('LLM_MODEL') ?? null,
        EMBEDDING_MODEL: envValue('EMBEDDING_MODEL') ?? null,
        runs: 0,
      },
      blockers,
      commands: lines.map((l) => ({ cmd: l.cmd, ok: l.ok, out: l.out })),
      steps,
      video: video ?? null,
      defects: [],
    })
  }
})
