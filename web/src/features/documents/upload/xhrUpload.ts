// Tải lên bằng XMLHttpRequest để có `upload.onprogress` (T10) — ngoài shared/api chỉ thư mục này được dùng XHR (R-3).

export interface UploadResponse {
  status: number
  body: unknown
}

/** Lỗi mạng (onerror/ontimeout): được phép thử lại. */
export class UploadNetworkError extends Error {
  constructor() {
    super('network')
    this.name = 'UploadNetworkError'
  }
}

export interface UploadHandle {
  done: Promise<UploadResponse>
  abort: () => void
}

export type Uploader = (file: File, batchSize: number, onProgress: (percent: number) => void) => UploadHandle

export const xhrUpload: Uploader = (file, batchSize, onProgress) => {
  const xhr = new XMLHttpRequest()
  const done = new Promise<UploadResponse>((resolve, reject) => {
    xhr.open('POST', '/api/documents')
    xhr.withCredentials = true
    xhr.timeout = 5 * 60_000
    xhr.setRequestHeader('X-Requested-With', 'fetch')
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('X-Batch-Size', String(batchSize))
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.min(99, Math.round((e.loaded / e.total) * 100)))
    }
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        body = null
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new UploadNetworkError())
    xhr.ontimeout = () => reject(new UploadNetworkError())
    xhr.onabort = () => reject(new DOMException('aborted', 'AbortError'))
    const form = new FormData()
    form.append('file', file, file.name)
    xhr.send(form)
  })
  return { done, abort: () => xhr.abort() }
}
