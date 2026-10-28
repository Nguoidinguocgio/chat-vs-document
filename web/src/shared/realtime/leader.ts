// Bầu "tab giữ kết nối" bằng Web Locks: chỉ một tab mở EventSource, phát lại sự kiện cho tab khác qua BroadcastChannel.
// Tab giữ kết nối đóng → khóa được nhả → tab kế tiếp tự trở thành người giữ.
export const LOCK_NAME = 'cvd-sse-leader'
export const CHANNEL_NAME = 'cvd-events'

export function canElectLeader(): boolean {
  return typeof navigator !== 'undefined' && !!navigator.locks && typeof BroadcastChannel !== 'undefined'
}

/** Gọi `becomeLeader` khi tab này giành được khóa; khóa giữ tới khi `stop()` được gọi. */
export function electLeader(becomeLeader: () => void): () => void {
  let release: () => void = () => {}
  const held = new Promise<void>((resolve) => (release = resolve))
  const abort = new AbortController()
  navigator.locks
    .request(LOCK_NAME, { signal: abort.signal }, async () => {
      becomeLeader()
      await held
    })
    .catch(() => {
      // bị hủy trước khi giành được khóa
    })
  return () => {
    release()
    abort.abort()
  }
}
