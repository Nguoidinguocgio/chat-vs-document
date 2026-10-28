import { CHANNEL_NAME, canElectLeader, electLeader } from './leader'

// Kênh sự kiện workspace `/api/events` (T11 mục 3.1) — nơi duy nhất được dùng EventSource (luật R-3).
export const EVENT_TYPES = ['document.created', 'document.status_changed', 'document.deleted', 'session.revoked', 'resync', 'heartbeat'] as const

export interface WorkspaceEvent {
  type: string
  data: unknown
}

export interface ConnectOptions {
  url?: string
  /** Cho test thay EventSource. */
  EventSourceImpl?: typeof EventSource
  /** Dự phòng khi proxy đệm SSE (VITE_POLL_FALLBACK): gọi định kỳ khi SSE lỗi liên tục ≥ 3 lần. */
  onPoll?: () => void
  pollFallback?: boolean
  /** Tắt bầu tab giữ kết nối (test). */
  singleTab?: boolean
  random?: () => number
}

export const BASE_DELAY_MS = 1000
export const MAX_DELAY_MS = 30_000
export const POLL_INTERVAL_MS = 5000

/** Độ trễ kết nối lại thứ `attempt` (0-based): 1 s, 2 s, 4 s … tối đa 30 s, ±20% ngẫu nhiên (ALG-03). */
export function reconnectDelay(attempt: number, random: () => number = Math.random): number {
  const base = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** attempt)
  return Math.round(base * (0.8 + random() * 0.4))
}

/**
 * Mở kênh sự kiện. Sau khi kết nối lại thành công (từng lỗi) phát một `resync` giả để client tải lại
 * danh sách (phòng sự kiện bị lỡ). Trả hàm ngắt kết nối.
 */
export function connectWorkspaceEvents(onEvent: (e: WorkspaceEvent) => void, opts: ConnectOptions = {}): () => void {
  const url = opts.url ?? '/api/events'
  const Impl = opts.EventSourceImpl ?? (typeof EventSource === 'undefined' ? undefined : EventSource)
  if (!Impl) return () => {}
  const ES: typeof EventSource = Impl
  const random = opts.random ?? Math.random
  const multiTab = !opts.singleTab && canElectLeader()
  const channel = multiTab ? new BroadcastChannel(CHANNEL_NAME) : null

  let es: EventSource | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  let pollTimer: ReturnType<typeof setInterval> | undefined
  let failures = 0
  let stopped = false

  const deliver = (e: WorkspaceEvent, broadcast: boolean) => {
    onEvent(e)
    if (broadcast) channel?.postMessage(e)
  }

  function stopPolling() {
    clearInterval(pollTimer)
    pollTimer = undefined
  }

  function open() {
    if (stopped) return
    const source = new ES(url, { withCredentials: true })
    es = source
    source.onopen = () => {
      if (failures > 0) deliver({ type: 'resync', data: {} }, true)
      failures = 0
      stopPolling()
    }
    for (const type of EVENT_TYPES) {
      source.addEventListener(type, (ev) => {
        let data: unknown = {}
        try {
          data = JSON.parse((ev as MessageEvent<string>).data || '{}')
        } catch {
          data = {}
        }
        deliver({ type, data }, true)
      })
    }
    source.onerror = () => {
      // CONNECTING: trình duyệt tự thử lại; CLOSED: tự lên lịch kết nối lại.
      if (source.readyState !== ES.CLOSED) return
      source.close()
      if (stopped) return
      const delay = reconnectDelay(failures, random)
      failures += 1
      if (opts.pollFallback && failures >= 3 && !pollTimer && opts.onPoll) pollTimer = setInterval(opts.onPoll, POLL_INTERVAL_MS)
      timer = setTimeout(open, delay)
    }
  }

  let stopLeader: () => void = () => {}
  if (multiTab) {
    channel!.onmessage = (m: MessageEvent<WorkspaceEvent>) => deliver(m.data, false)
    stopLeader = electLeader(open)
  } else {
    open()
  }

  return () => {
    stopped = true
    clearTimeout(timer)
    stopPolling()
    es?.close()
    stopLeader()
    channel?.close()
  }
}
