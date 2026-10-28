// Kiểm tra file phía client (T10 mục 3.1, ALG-02) — hàm thuần, không phụ thuộc UI. Máy chủ vẫn kiểm tra lại.
export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const MAX_DOCS = 20

export type RejectionKey = 'documents.not_pdf' | 'documents.too_large' | 'documents.limit_reached'

export interface ValidationResult {
  accepted: File[]
  rejected: { file: File; messageKey: RejectionKey }[]
}

// FileReader thay vì Blob.arrayBuffer(): có ở mọi trình duyệt hỗ trợ và cả jsdom.
function readHead(file: File): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => {
      const buf = new Uint8Array(reader.result as ArrayBuffer)
      resolve(String.fromCharCode(...buf))
    }
    reader.onerror = () => resolve('')
    reader.readAsArrayBuffer(file.slice(0, 5))
  })
}

async function hasPdfMagic(file: File): Promise<boolean> {
  return (await readHead(file)) === '%PDF-'
}

/**
 * Duyệt theo thứ tự chọn; `existingCount` gồm mọi tài liệu (kể cả đang xử lý, lỗi) và file đang tải lên.
 * File không hợp lệ không chiếm chỗ.
 */
export async function validateFiles(files: readonly File[], existingCount: number): Promise<ValidationResult> {
  let slots = MAX_DOCS - existingCount
  const out: ValidationResult = { accepted: [], rejected: [] }
  for (const file of files) {
    if (!file.name.toLowerCase().endsWith('.pdf') || !(await hasPdfMagic(file))) {
      out.rejected.push({ file, messageKey: 'documents.not_pdf' })
    } else if (file.size > MAX_FILE_BYTES) {
      out.rejected.push({ file, messageKey: 'documents.too_large' })
    } else if (slots <= 0) {
      out.rejected.push({ file, messageKey: 'documents.limit_reached' })
    } else {
      out.accepted.push(file)
      slots -= 1
    }
  }
  return out
}
