import { useQuery } from '@tanstack/vue-query'
import { request, type Conversation, type ConversationDetail } from '@/shared/api'

export const CONVERSATIONS_KEY = ['conversations'] as const

export const conversationsApi = {
  list: () => request<Conversation[]>('GET', '/api/conversations'),
  create: () => request<Conversation>('POST', '/api/conversations'),
  get: (id: string, signal?: AbortSignal) => request<ConversationDetail>('GET', `/api/conversations/${id}`, { signal }),
}

/** Danh sách hội thoại của người dùng (T15 mục 3.1); làm mới khi quay lại tab. */
export function useConversations() {
  return useQuery({ queryKey: CONVERSATIONS_KEY, queryFn: conversationsApi.list, refetchOnWindowFocus: true })
}

export type ConversationGroup = 'today' | 'last_7_days' | 'last_30_days' | 'older'

const TZ = 'Asia/Ho_Chi_Minh'
const dayFmt = new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' })

/** Số ngày lịch (theo giờ Việt Nam) giữa hai thời điểm. */
function daysBetween(then: Date, now: Date): number {
  const a = Date.parse(dayFmt.format(then))
  const b = Date.parse(dayFmt.format(now))
  return Math.round((b - a) / 86_400_000)
}

export function groupOf(lastActivity: string, now = new Date()): ConversationGroup {
  const d = daysBetween(new Date(lastActivity), now)
  if (d <= 0) return 'today'
  if (d <= 7) return 'last_7_days'
  if (d <= 30) return 'last_30_days'
  return 'older'
}

/** Nhóm "Hôm nay / 7 ngày trước / 30 ngày trước / Cũ hơn", mới hoạt động nhất trước. */
export function groupConversations(list: Conversation[], now = new Date()): { group: ConversationGroup; items: Conversation[] }[] {
  const sorted = [...list].sort((x, y) => Date.parse(y.last_activity_at) - Date.parse(x.last_activity_at))
  const out: { group: ConversationGroup; items: Conversation[] }[] = []
  for (const c of sorted) {
    const g = groupOf(c.last_activity_at, now)
    if (out.at(-1)?.group !== g) out.push({ group: g, items: [] })
    out.at(-1)!.items.push(c)
  }
  return out
}
