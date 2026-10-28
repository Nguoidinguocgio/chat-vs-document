import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setGlobalErrorHandler } from '@/shared/api'
import { MAX_CONCURRENT, useUploadQueue } from './queue'
import { MAX_FILE_BYTES, validateFiles } from './validateFiles'
import { UploadNetworkError, type UploadResponse, type Uploader } from './xhrUpload'

const pdf = (name: string, size = 100) => {
  const head = new TextEncoder().encode('%PDF-1.7\n')
  const body = new Uint8Array(Math.max(size, head.length))
  body.set(head)
  return new File([body], name, { type: 'application/pdf' })
}

describe('validateFiles', () => {
  const cases: Record<string, () => File> = {
    '.docx': () => new File(['PK..'], 'a.docx'),
    '.pdf giả (không có %PDF-)': () => new File(['xin chao'], 'gia.pdf'),
    '10 MB + 1 byte': () => pdf('lon.pdf', MAX_FILE_BYTES + 1),
  }
  it.each([
    ['.docx', 'documents.not_pdf'],
    ['.pdf giả (không có %PDF-)', 'documents.not_pdf'],
    ['10 MB + 1 byte', 'documents.too_large'],
  ])('%s → %s', async (name, key) => {
    const r = await validateFiles([cases[name]()], 0)
    expect(r.accepted).toEqual([])
    expect(r.rejected[0].messageKey).toBe(key)
  })

  it('đúng 10 MB được nhận', async () => {
    expect((await validateFiles([pdf('vua.pdf', MAX_FILE_BYTES)], 0)).accepted).toHaveLength(1)
  })

  it('AC-010.5: 18 tài liệu + 4 file → nhận 2 file đầu theo thứ tự chọn, từ chối 2 file sau', async () => {
    const files = ['1.pdf', '2.pdf', '3.pdf', '4.pdf'].map((n) => pdf(n))
    const r = await validateFiles(files, 18)
    expect(r.accepted.map((f) => f.name)).toEqual(['1.pdf', '2.pdf'])
    expect(r.rejected.map((x) => [x.file.name, x.messageKey])).toEqual([
      ['3.pdf', 'documents.limit_reached'],
      ['4.pdf', 'documents.limit_reached'],
    ])
  })

  it('20 tài liệu + 1 file → limit_reached; file hỏng không chiếm chỗ', async () => {
    expect((await validateFiles([pdf('a.pdf')], 20)).rejected[0].messageKey).toBe('documents.limit_reached')
    const r = await validateFiles([new File(['x'], 'hong.pdf'), pdf('ok.pdf')], 19)
    expect(r.accepted.map((f) => f.name)).toEqual(['ok.pdf'])
    expect(r.rejected[0].messageKey).toBe('documents.not_pdf')
  })
})

/** Uploader giả: mỗi lần gọi trả một lời hứa test tự giải quyết. */
function fakeUploader() {
  const calls: { file: File; resolve: (r: UploadResponse) => void; reject: (e: unknown) => void; progress: (p: number) => void }[] = []
  let inFlight = 0
  let maxInFlight = 0
  const uploader: Uploader = (file, _batch, onProgress) => {
    inFlight++
    maxInFlight = Math.max(maxInFlight, inFlight)
    let resolve!: (r: UploadResponse) => void
    let reject!: (e: unknown) => void
    const done = new Promise<UploadResponse>((res, rej) => {
      resolve = (r) => {
        inFlight--
        res(r)
      }
      reject = (e) => {
        inFlight--
        rej(e)
      }
    })
    calls.push({ file, resolve, reject, progress: onProgress })
    return { done, abort: () => reject(new DOMException('aborted', 'AbortError')) }
  }
  return { uploader, calls, max: () => maxInFlight }
}

const created = (name: string) => ({ status: 201, body: { id: name, name, status: 'processing' } })
const flush = () => new Promise((r) => setTimeout(r, 0))

describe('hàng đợi tải lên', () => {
  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => vi.useRealTimers())

  it('AC-010.3: 5 file, file thứ 3 lỗi, 4 file kia vẫn xong; không quá 2 request song song', async () => {
    const fake = fakeUploader()
    const onCreated = vi.fn()
    const q = useUploadQueue()
    q.configure({ uploader: fake.uploader, onCreated })
    q.enqueue(['1', '2', '3', '4', '5'].map((n) => pdf(`${n}.pdf`)))
    expect(fake.calls).toHaveLength(2)
    expect(q.tasks.filter((t) => t.phase === 'uploading')).toHaveLength(2)
    fake.calls[0].progress(40)
    expect(q.tasks[0].progress).toBe(40)

    for (let i = 0; i < 5; i++) {
      await flush()
      const c = fake.calls[i]
      if (c.file.name === '3.pdf') c.resolve({ status: 400, body: { error: { code: 'NOT_PDF', message_key: 'documents.not_pdf', details: {} } } })
      else c.resolve(created(c.file.name))
      await flush()
    }
    expect(fake.max()).toBeLessThanOrEqual(MAX_CONCURRENT)
    expect(onCreated).toHaveBeenCalledTimes(4)
    expect(q.tasks).toEqual([])
    expect(q.rejections).toMatchObject([{ fileName: '3.pdf', messageKey: 'documents.not_pdf' }])
  })

  it('lỗi mạng: tự thử lại 3 lần sau 1 s / 2 s / 4 s rồi "Gián đoạn"; bấm Thử lại thì thành công', async () => {
    vi.useFakeTimers()
    const fake = fakeUploader()
    const q = useUploadQueue()
    q.configure({ uploader: fake.uploader, onCreated: () => {} })
    q.enqueue([pdf('mang.pdf')])
    const fail = async () => {
      fake.calls.at(-1)!.reject(new UploadNetworkError())
      await vi.advanceTimersByTimeAsync(0)
    }
    await fail()
    expect(q.tasks[0]).toMatchObject({ phase: 'retrying', attempts: 1 })
    await vi.advanceTimersByTimeAsync(999)
    expect(fake.calls).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(fake.calls).toHaveLength(2)
    await fail()
    await vi.advanceTimersByTimeAsync(2000)
    expect(fake.calls).toHaveLength(3)
    await fail()
    await vi.advanceTimersByTimeAsync(4000)
    expect(fake.calls).toHaveLength(4)
    await fail()
    expect(q.tasks[0].phase).toBe('interrupted')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(fake.calls).toHaveLength(4)

    q.retry(q.tasks[0].localId)
    expect(fake.calls).toHaveLength(5)
    fake.calls[4].resolve(created('mang.pdf'))
    await vi.advanceTimersByTimeAsync(0)
    expect(q.tasks).toEqual([])
  })

  it('409 DOCUMENT_LIMIT_REACHED từ máy chủ (cạnh tranh) → thông báo, không có "tài liệu ma"', async () => {
    const fake = fakeUploader()
    const onCreated = vi.fn()
    const q = useUploadQueue()
    q.configure({ uploader: fake.uploader, onCreated })
    q.enqueue([pdf('tre.pdf')])
    fake.calls[0].resolve({ status: 409, body: { error: { code: 'DOCUMENT_LIMIT_REACHED', message_key: 'documents.limit_reached', details: { limit: 20 } } } })
    await flush()
    expect(onCreated).not.toHaveBeenCalled()
    expect(q.tasks).toEqual([])
    expect(q.rejections[0].messageKey).toBe('documents.limit_reached')
  })

  it('401 từ máy chủ đi qua xử lý toàn cục của shared/api; 4xx không thử lại', async () => {
    const handler = vi.fn()
    setGlobalErrorHandler(handler)
    const fake = fakeUploader()
    const q = useUploadQueue()
    q.configure({ uploader: fake.uploader })
    q.enqueue([pdf('a.pdf')])
    fake.calls[0].resolve({ status: 401, body: { error: { code: 'SESSION_EXPIRED', message_key: 'errors.session_expired', details: {} } } })
    await flush()
    expect(handler.mock.calls[0][0].code).toBe('SESSION_EXPIRED')
    expect(fake.calls).toHaveLength(1)
    setGlobalErrorHandler(() => {})
  })

  it('Hủy bỏ task đang tải và nhường chỗ cho file kế tiếp', async () => {
    const fake = fakeUploader()
    const q = useUploadQueue()
    q.configure({ uploader: fake.uploader })
    q.enqueue([pdf('1.pdf'), pdf('2.pdf'), pdf('3.pdf')])
    q.cancel(q.tasks[0].localId)
    await flush()
    expect(fake.calls.map((c) => c.file.name)).toEqual(['1.pdf', '2.pdf', '3.pdf'])
    expect(q.tasks.map((t) => t.file.name)).toEqual(['2.pdf', '3.pdf'])
  })
})
