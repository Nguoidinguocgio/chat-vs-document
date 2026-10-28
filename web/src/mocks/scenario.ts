// Kịch bản API giả (T06 mục 3.4). Dev: thêm ?mock=<tên> vào URL (được nhớ trong sessionStorage của tab).
export const SCENARIOS = [
  'answered',
  'not_found',
  'partial',
  'interrupted',
  'timeout',
  'llm_error',
  'empty_workspace',
  'processing_only',
  'all_failed',
  'near_limit',
] as const

export type Scenario = (typeof SCENARIOS)[number]

const KEY = 'mock:scenario'
let current: Scenario = 'answered'

function isScenario(v: string | null): v is Scenario {
  return v !== null && (SCENARIOS as readonly string[]).includes(v)
}

export function initScenarioFromUrl(): void {
  try {
    const fromUrl = new URLSearchParams(window.location.search).get('mock')
    if (isScenario(fromUrl)) sessionStorage.setItem(KEY, fromUrl)
    const saved = sessionStorage.getItem(KEY)
    if (isScenario(saved)) current = saved
  } catch {
    // sessionStorage không dùng được (chế độ riêng tư) → giữ mặc định
  }
}

export function getScenario(): Scenario {
  return current
}

export function setScenario(s: Scenario): void {
  current = s
}
