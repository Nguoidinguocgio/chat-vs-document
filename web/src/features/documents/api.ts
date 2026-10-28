import type { QueryClient } from '@tanstack/vue-query'
import { request, type Document, type DocumentList } from '@/shared/api'

export const DOCUMENTS_KEY = ['documents'] as const
export const MAX_DOCS = 20

export const documentsApi = {
  list: () => request<DocumentList>('GET', '/api/documents'),
  retry: (id: string) => request<Document>('POST', `/api/documents/${id}/retry`),
}

function withItems(old: DocumentList | undefined, items: Document[]): DocumentList {
  return { items, count: items.length, limit: old?.limit ?? MAX_DOCS }
}

/** Thêm hoặc thay tài liệu trong cache danh sách (mới nhất ở đầu). */
export function upsertDocument(qc: QueryClient, doc: Document): void {
  qc.setQueryData<DocumentList>(DOCUMENTS_KEY, (old) => {
    if (!old) return old
    const exists = old.items.some((d) => d.id === doc.id)
    const items = exists ? old.items.map((d) => (d.id === doc.id ? doc : d)) : [doc, ...old.items]
    return withItems(old, items)
  })
}

export function removeDocument(qc: QueryClient, id: string): void {
  qc.setQueryData<DocumentList>(DOCUMENTS_KEY, (old) => (old ? withItems(old, old.items.filter((d) => d.id !== id)) : old))
}
