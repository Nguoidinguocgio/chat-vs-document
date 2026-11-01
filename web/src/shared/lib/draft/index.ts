// Bản nháp câu hỏi (T05 mục 3.3): chỉ ở sessionStorage của tab, khóa draft:{userId}:{conversationId}.
// Giữ lại khi phiên hết hạn (SESSION_EXPIRED); xóa khi đăng xuất hoặc bị thu hồi phiên.
const PREFIX = 'draft:'
export const DRAFT_DEBOUNCE_MS = 500

const timers = new Map<string, ReturnType<typeof setTimeout>>()
const pending = new Map<string, string>()
/** Câu hỏi đã bấm Gửi nhưng máy chủ chưa nhận: giữ làm nháp cho tới {@link releaseDraft} (AC-001.5). */
const held = new Set<string>()

const keyOf = (userId: string, conversationId: string) => `${PREFIX}${userId}:${conversationId}`

function write(key: string, text: string): void {
  try {
    if (text) sessionStorage.setItem(key, text)
    else sessionStorage.removeItem(key)
  } catch {
    // sessionStorage bị chặn: bỏ qua, nháp chỉ là tiện ích
  }
}

/** Lưu có debounce 500 ms; gọi {@link flushDrafts} trước khi rời trang để không mất ký tự cuối. */
export function saveDraft(userId: string, conversationId: string, text: string): void {
  const key = keyOf(userId, conversationId)
  if (held.has(key)) {
    // Ô nhập vừa được xóa sau khi Gửi: không xóa câu hỏi đang giữ. Gõ nội dung mới thì bỏ giữ.
    if (!text) return
    held.delete(key)
  }
  pending.set(key, text)
  clearTimeout(timers.get(key))
  timers.set(
    key,
    setTimeout(() => {
      timers.delete(key)
      pending.delete(key)
      write(key, text)
    }, DRAFT_DEBOUNCE_MS),
  )
}

export function flushDrafts(): void {
  for (const [key, text] of pending) {
    clearTimeout(timers.get(key))
    write(key, text)
  }
  timers.clear()
  pending.clear()
}

export function loadDraft(userId: string, conversationId: string): string {
  const key = keyOf(userId, conversationId)
  held.delete(key) // đã nạp lại vào ô nhập → thành nháp thường
  if (pending.has(key)) return pending.get(key) ?? ''
  try {
    return sessionStorage.getItem(key) ?? ''
  } catch {
    return ''
  }
}

export function clearDraft(userId: string, conversationId: string): void {
  const key = keyOf(userId, conversationId)
  clearTimeout(timers.get(key))
  timers.delete(key)
  pending.delete(key)
  held.delete(key)
  write(key, '')
}

/**
 * Bấm Gửi: ghi ngay câu hỏi vào sessionStorage và giữ nó dù ô nhập đã trống, để nếu phiên hết hạn
 * trước khi máy chủ nhận câu hỏi thì đăng nhập lại vẫn còn (AC-001.5). Gọi {@link releaseDraft} khi đã gửi xong.
 */
export function holdDraft(userId: string, conversationId: string, text: string): void {
  const key = keyOf(userId, conversationId)
  clearTimeout(timers.get(key))
  timers.delete(key)
  pending.delete(key)
  held.add(key)
  write(key, text)
}

/** Máy chủ đã nhận (hoặc từ chối không vì phiên) câu hỏi đang giữ → xóa nháp; bỏ qua nếu người dùng đã gõ nháp mới. */
export function releaseDraft(userId: string, conversationId: string): void {
  const key = keyOf(userId, conversationId)
  if (held.delete(key)) write(key, '')
}

export function clearAllDrafts(): void {
  timers.forEach((t) => clearTimeout(t))
  timers.clear()
  pending.clear()
  held.clear()
  try {
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const k = sessionStorage.key(i)
      if (k?.startsWith(PREFIX)) sessionStorage.removeItem(k)
    }
  } catch {
    // bỏ qua
  }
}
