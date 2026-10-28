import { useQuery, useQueryClient, type QueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import type { Document, DocumentList } from '@/shared/api'
import { t } from '@/shared/lib/i18n'
import { toast } from '@/shared/ui'
import { DOCUMENTS_KEY, documentsApi, removeDocument, upsertDocument } from './api'
import { useUploadQueue } from './upload/queue'

export function useDocuments() {
  return useQuery({ queryKey: DOCUMENTS_KEY, queryFn: documentsApi.list, staleTime: 30_000 })
}

export interface DocumentsSummary {
  total: number
  ready: number
  processing: number
  failed: number
  hasReady: boolean
}

/** Tóm tắt trạng thái cho ô nhập/trạng thái trống (T11 mục 3.4); file đang tải lên tính vào `processing`. */
export function useDocumentsSummary() {
  const { data } = useDocuments()
  const queue = useUploadQueue()
  return computed<DocumentsSummary>(() => {
    const items = data.value?.items ?? []
    const uploading = queue.tasks.length
    const ready = items.filter((d) => d.status === 'ready').length
    const processing = items.filter((d) => d.status === 'processing').length + uploading
    const failed = items.filter((d) => d.status === 'failed').length
    return { total: items.length + uploading, ready, processing, failed, hasReady: ready > 0 }
  })
}

/** Thông báo cho trình đọc màn hình khi trạng thái tài liệu đổi (vùng aria-live của bảng Tài liệu). */
export const statusAnnouncement = ref('')

function readyCount(qc: QueryClient): number {
  return qc.getQueryData<DocumentList>(DOCUMENTS_KEY)?.items.filter((d) => d.status === 'ready').length ?? 0
}

/**
 * Áp một sự kiện `document.*` / `resync` của kênh SSE vào cache (T11 mục 3.1). Toast "Đã sẵn sàng"
 * chỉ hiện khi tài liệu ĐẦU TIÊN của workspace chuyển sang sẵn sàng.
 */
export function applyDocumentEvent(qc: QueryClient, type: string, data: unknown): void {
  if (type === 'resync') {
    void qc.invalidateQueries({ queryKey: DOCUMENTS_KEY })
    return
  }
  if (type === 'document.deleted') {
    removeDocument(qc, (data as { id: string }).id)
    return
  }
  if (type !== 'document.created' && type !== 'document.status_changed') return
  const doc = data as Document
  const before = readyCount(qc)
  const prev = qc.getQueryData<DocumentList>(DOCUMENTS_KEY)?.items.find((d) => d.id === doc.id)
  upsertDocument(qc, doc)
  if (prev && prev.status !== doc.status) {
    if (doc.status === 'ready') statusAnnouncement.value = t('documents.announce_ready', { name: doc.name })
    if (doc.status === 'failed') statusAnnouncement.value = t('documents.announce_failed', { name: doc.name })
  }
  if (before === 0 && doc.status === 'ready' && readyCount(qc) === 1) toast.show(t('documents.first_ready_toast'))
}

/** Thử lại tài liệu lỗi hệ thống: chuyển hàng về "Đang xử lý" ngay (lạc quan), sau đó theo SSE. */
export function useRetryDocument() {
  const qc = useQueryClient()
  return async (doc: Document) => {
    upsertDocument(qc, { ...doc, status: 'processing', error_code: null })
    try {
      upsertDocument(qc, await documentsApi.retry(doc.id))
    } catch {
      upsertDocument(qc, doc)
      toast.error(t('errors.generic'))
    }
  }
}
