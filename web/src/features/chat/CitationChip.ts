import type { Citation } from '@/shared/api'
import { t } from '@/shared/lib/i18n'
import type { ChipInfo } from '@/shared/lib/markdown'

/**
 * Dữ liệu hiển thị chip `[n]` (CMP-06). Nút thật do `shared/lib/markdown` dựng bằng DOM API sau khi
 * làm sạch (không mount được component Vue vào HTML đã render); ở đây chỉ quyết định nhãn/kiểu.
 */
export function chipInfo(n: number, c: Citation, presentDocs: ReadonlySet<string> | null): ChipInfo {
  const gone = c.document_deleted || (presentDocs !== null && !presentDocs.has(c.document_id))
  const name = gone ? t('chat.document_deleted') : c.document_name
  return {
    label: t('chat.citation_label', { n, name, page: c.page }),
    title: t('chat.citation_title', { name, page: c.page }),
    gone,
  }
}

export function chipMap(citations: Record<number, Citation>, presentDocs: ReadonlySet<string> | null): Map<number, ChipInfo> {
  return new Map(Object.values(citations).map((c) => [c.index, chipInfo(c.index, c, presentDocs)]))
}
