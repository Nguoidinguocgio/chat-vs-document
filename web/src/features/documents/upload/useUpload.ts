import { useQueryClient } from '@tanstack/vue-query'
import type { DocumentList } from '@/shared/api'
import { DOCUMENTS_KEY, upsertDocument } from '../api'
import { useUploadQueue } from './queue'
import { validateFiles } from './validateFiles'

/** Luồng chọn/thả file dùng chung cho nút "Chọn file", vùng kéo-thả và lớp phủ toàn trang. */
export function useUpload() {
  const qc = useQueryClient()
  const queue = useUploadQueue()
  queue.configure({ onCreated: (doc) => upsertDocument(qc, doc) })

  // Tài liệu hiện có (mọi trạng thái) + file đang chờ/tải lên đều chiếm chỗ trong giới hạn 20.
  // Đọc tại thời điểm chọn file (getQueryData không phản ứng nên không dùng computed).
  const occupied = () => (qc.getQueryData<DocumentList>(DOCUMENTS_KEY)?.count ?? 0) + queue.tasks.length

  async function addFiles(list: FileList | File[] | null | undefined) {
    const files = Array.from(list ?? [])
    if (files.length === 0) return
    const { accepted, rejected } = await validateFiles(files, occupied())
    rejected.forEach((r) => queue.reject(r.file.name, r.messageKey))
    if (accepted.length) queue.enqueue(accepted)
  }

  return { addFiles, queue, occupied }
}
