import { describe, expect, it, vi } from 'vitest'
import { parseSseStream, type SseEvent } from './parseSseStream'

const enc = new TextEncoder()

function streamOf(chunks: Uint8Array[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(c) {
      chunks.forEach((x) => c.enqueue(x))
      c.close()
    },
  })
}

async function collect(s: ReadableStream<Uint8Array>, onActivity?: () => void): Promise<SseEvent[]> {
  const out: SseEvent[] = []
  for await (const e of parseSseStream(s, { onActivity })) out.push(e)
  return out
}

const RAW =
  ': connected\n\n' +
  'event: meta\ndata: {"message_id":"m1","lang":"vi"}\n\n' +
  'event: delta\r\ndata: {"text":"Thời hạn thanh toán được "}\r\n\r\n' +
  ': ping\n\n' +
  'event: delta\ndata: {"text":"quy định [1]"}\n\n' +
  'event: citation\nid: 7\ndata: {"index":1,\ndata: "page":12}\n\n' +
  'event: done\ndata: {"answer_type":"answered","missing":null}\n\n'

const EXPECTED: SseEvent[] = [
  { event: 'meta', data: { message_id: 'm1', lang: 'vi' } },
  { event: 'delta', data: { text: 'Thời hạn thanh toán được ' } },
  { event: 'delta', data: { text: 'quy định [1]' } },
  { event: 'citation', data: { index: 1, page: 12 }, id: '7' },
  // theo chuẩn SSE, id cuối cùng được giữ cho các sự kiện sau
  { event: 'done', data: { answer_type: 'answered', missing: null }, id: '7' },
]

describe('parseSseStream', () => {
  it('đúng thứ tự, bỏ ping/comment, nối data nhiều dòng, nhận \\r\\n', async () => {
    expect(await collect(streamOf([enc.encode(RAW)]))).toEqual(EXPECTED)
  })

  it('kết quả không đổi khi luồng byte bị cắt ở MỌI vị trí (kể cả giữa ký tự UTF-8 "ờ", "ượ" và giữa \\n\\n)', async () => {
    const bytes = enc.encode(RAW)
    for (let i = 1; i < bytes.length; i++) {
      expect(await collect(streamOf([bytes.slice(0, i), bytes.slice(i)])), `cắt tại ${i}`).toEqual(EXPECTED)
    }
  })

  it('cắt ngẫu nhiên thành nhiều mảnh', async () => {
    const bytes = enc.encode(RAW)
    let seed = 42
    const rnd = () => ((seed = (seed * 1103515245 + 12345) % 2 ** 31) / 2 ** 31)
    for (let run = 0; run < 50; run++) {
      const chunks: Uint8Array[] = []
      let at = 0
      while (at < bytes.length) {
        const n = 1 + Math.floor(rnd() * 7)
        chunks.push(bytes.slice(at, at + n))
        at += n
      }
      expect(await collect(streamOf(chunks))).toEqual(EXPECTED)
    }
  })

  it('JSON lỗi: bỏ khung đó, luồng tiếp tục', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const s = streamOf([enc.encode('event: delta\ndata: {hỏng\n\nevent: done\ndata: {"answer_type":"not_found"}\n\n')])
    expect(await collect(s)).toEqual([{ event: 'done', data: { answer_type: 'not_found' } }])
    warn.mockRestore()
  })

  it('khung cuối không có dòng trống vẫn được nhận khi luồng đóng; onActivity gọi cho mọi mảnh (cả ping)', async () => {
    const activity = vi.fn()
    const s = streamOf([enc.encode(': ping\n\n'), enc.encode('event: done\ndata: {"answer_type":"answered"}')])
    expect(await collect(s, activity)).toEqual([{ event: 'done', data: { answer_type: 'answered' } }])
    expect(activity).toHaveBeenCalledTimes(2)
  })
})
