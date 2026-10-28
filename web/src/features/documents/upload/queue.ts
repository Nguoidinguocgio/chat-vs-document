import { defineStore } from 'pinia'
import { markRaw, ref } from 'vue'
import { ApiError, reportApiError, type Document } from '@/shared/api'
import { registerReset } from '@/shared/lib/reset'
import { UploadNetworkError, xhrUpload, type UploadHandle, type Uploader } from './xhrUpload'

export const MAX_CONCURRENT = 2
export const RETRY_DELAYS_MS = [1000, 2000, 4000] as const

export interface UploadTask {
  localId: string
  file: File
  progress: number
  /** `retrying`: lỗi mạng, đang chờ thử lại tự động (`attempts`/3). */
  phase: 'queued' | 'uploading' | 'retrying' | 'interrupted' | 'failed'
  attempts: number
  batchSize: number
}

export interface Rejection {
  id: string
  fileName: string
  messageKey: string
  params?: Record<string, unknown>
}

const REJECT_KEYS: Record<string, string> = {
  NOT_PDF: 'documents.not_pdf',
  FILE_TOO_LARGE: 'documents.too_large',
  DOCUMENT_LIMIT_REACHED: 'documents.limit_reached',
}

let seq = 0
const nextId = (p: string) => `${p}-${++seq}`

/** Hàng đợi tải lên (T10 mục 3.3): tối đa 2 file đồng thời; một file lỗi không dừng các file khác. */
export const useUploadQueue = defineStore('upload-queue', () => {
  const tasks = ref<UploadTask[]>([])
  const rejections = ref<Rejection[]>([])
  const handles = new Map<string, UploadHandle>()
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  let uploader: Uploader = xhrUpload
  let onCreated: (doc: Document) => void = () => {}

  function configure(opts: { uploader?: Uploader; onCreated?: (doc: Document) => void }) {
    if (opts.uploader) uploader = opts.uploader
    if (opts.onCreated) onCreated = opts.onCreated
  }

  const find = (id: string) => tasks.value.find((t) => t.localId === id)
  const active = () => tasks.value.filter((t) => t.phase === 'uploading' || t.phase === 'retrying').length

  function remove(id: string) {
    tasks.value = tasks.value.filter((t) => t.localId !== id)
    handles.delete(id)
    clearTimeout(timers.get(id))
    timers.delete(id)
  }

  function reject(fileName: string, messageKey: string, params?: Record<string, unknown>) {
    rejections.value.push({ id: nextId('rej'), fileName, messageKey, params })
  }

  function enqueue(files: File[]) {
    for (const file of files) {
      tasks.value.push({ localId: nextId('up'), file: markRaw(file), progress: 0, phase: 'queued', attempts: 0, batchSize: files.length })
    }
    pump()
  }

  function pump() {
    for (const t of tasks.value) {
      if (active() >= MAX_CONCURRENT) return
      if (t.phase === 'queued') start(t.localId)
    }
  }

  function start(id: string) {
    const t = find(id)
    if (!t) return
    t.phase = 'uploading'
    t.progress = 0
    const handle = uploader(t.file, t.batchSize, (p) => {
      const cur = find(id)
      if (cur) cur.progress = p
    })
    handles.set(id, handle)
    handle.done.then(
      (res) => finish(id, res.status, res.body),
      (e) => failNetwork(id, e),
    )
  }

  function finish(id: string, status: number, body: unknown) {
    const t = find(id)
    if (!t) return
    if (status === 201) {
      remove(id)
      onCreated(body as Document)
    } else {
      const e = toApiError(status, body)
      reportApiError(e)
      remove(id)
      if (REJECT_KEYS[e.code]) reject(t.file.name, REJECT_KEYS[e.code])
      else if (status === 401 || status === 403) {
        // phiên/quyền: lớp app chuyển trang
      } else reject(t.file.name, 'errors.generic')
    }
    pump()
  }

  function failNetwork(id: string, e: unknown) {
    const t = find(id)
    if (!t) return
    if (!(e instanceof UploadNetworkError)) {
      remove(id) // bị hủy
      pump()
      return
    }
    if (t.attempts < RETRY_DELAYS_MS.length) {
      const delay = RETRY_DELAYS_MS[t.attempts]
      t.attempts += 1
      t.phase = 'retrying'
      timers.set(
        id,
        setTimeout(() => {
          timers.delete(id)
          start(id)
        }, delay),
      )
    } else {
      t.phase = 'interrupted'
      pump()
    }
  }

  /** "Thử lại" sau khi hết 3 lần tự động. */
  function retry(id: string) {
    const t = find(id)
    if (!t) return
    t.attempts = 0
    t.phase = 'queued'
    pump()
  }

  function cancel(id: string) {
    handles.get(id)?.abort()
    remove(id)
    pump()
  }

  function dismissRejection(id: string) {
    rejections.value = rejections.value.filter((r) => r.id !== id)
  }

  function $reset() {
    handles.forEach((h) => h.abort())
    timers.forEach((t) => clearTimeout(t))
    handles.clear()
    timers.clear()
    tasks.value = []
    rejections.value = []
  }
  registerReset($reset)

  return { tasks, rejections, configure, enqueue, reject, retry, cancel, dismissRejection, $reset }
})

function toApiError(status: number, body: unknown): ApiError {
  const e = (body as { error?: { code?: string; message_key?: string; details?: Record<string, unknown> } } | null)?.error
  return e?.code ? new ApiError(status, e.code, e.message_key ?? 'errors.generic', e.details ?? {}) : ApiError.network(status)
}
