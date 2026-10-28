import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FakeEventSource } from '@/test/fakeEventSource'
import { connectWorkspaceEvents, reconnectDelay, type WorkspaceEvent } from './workspaceEvents'

const ES = FakeEventSource as unknown as typeof EventSource

describe('kênh sự kiện workspace', () => {
  beforeEach(() => {
    FakeEventSource.reset()
    vi.useFakeTimers()
  })
  afterEach(() => vi.useRealTimers())

  it('chuyển sự kiện có tên tới handler, bỏ qua JSON hỏng', () => {
    const got: WorkspaceEvent[] = []
    const stop = connectWorkspaceEvents((e) => got.push(e), { EventSourceImpl: ES, singleTab: true })
    const es = FakeEventSource.last
    expect(es.url).toBe('/api/events')
    expect(es.init?.withCredentials).toBe(true)
    es.open()
    es.emit('document.created', { id: 'd1' })
    es.emit('heartbeat', {})
    expect(got).toEqual([
      { type: 'document.created', data: { id: 'd1' } },
      { type: 'heartbeat', data: {} },
    ])
    stop()
  })

  it('kết nối lại theo 1 s, 2 s, 4 s … (±20%); kết nối được lại → phát resync để tải lại danh sách', async () => {
    const got: string[] = []
    const stop = connectWorkspaceEvents((e) => got.push(e.type), { EventSourceImpl: ES, singleTab: true, random: () => 0.5 })
    FakeEventSource.last.open()
    FakeEventSource.last.fail()
    expect(FakeEventSource.instances).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1000)
    expect(FakeEventSource.instances).toHaveLength(2)
    FakeEventSource.last.fail()
    await vi.advanceTimersByTimeAsync(1999)
    expect(FakeEventSource.instances).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(FakeEventSource.instances).toHaveLength(3)
    FakeEventSource.last.fail()
    await vi.advanceTimersByTimeAsync(4000)
    expect(FakeEventSource.instances).toHaveLength(4)
    FakeEventSource.last.open()
    expect(got).toEqual(['resync'])
    stop()
  })

  it('độ trễ tối đa 30 s và có nhiễu ±20%', () => {
    expect(reconnectDelay(0, () => 0.5)).toBe(1000)
    expect(reconnectDelay(3, () => 0.5)).toBe(8000)
    expect(reconnectDelay(10, () => 0.5)).toBe(30_000)
    expect(reconnectDelay(0, () => 0)).toBe(800)
    expect(reconnectDelay(0, () => 1)).toBe(1200)
  })

  it('dự phòng thăm dò mỗi 5 s sau ≥ 3 lỗi liên tiếp, tắt khi SSE phục hồi', async () => {
    const onPoll = vi.fn()
    const stop = connectWorkspaceEvents(() => {}, { EventSourceImpl: ES, singleTab: true, random: () => 0.5, pollFallback: true, onPoll })
    for (let i = 0; i < 3; i++) {
      FakeEventSource.last.fail()
      await vi.advanceTimersByTimeAsync(1000 * 2 ** i)
    }
    await vi.advanceTimersByTimeAsync(5000)
    expect(onPoll).toHaveBeenCalled()
    FakeEventSource.last.open()
    const n = onPoll.mock.calls.length
    await vi.advanceTimersByTimeAsync(20_000)
    expect(onPoll.mock.calls.length).toBe(n)
    stop()
  })

  it('ngắt kết nối thì không kết nối lại nữa', async () => {
    const stop = connectWorkspaceEvents(() => {}, { EventSourceImpl: ES, singleTab: true })
    stop()
    FakeEventSource.last.fail()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('không có EventSource (trình duyệt cũ/jsdom) → không làm gì, không lỗi', () => {
    expect(() => connectWorkspaceEvents(() => {})()).not.toThrow()
  })
})
