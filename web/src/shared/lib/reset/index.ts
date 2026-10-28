// Đăng ký hàm reset trạng thái phía client (store Pinia, bộ nhớ đệm feature) để xóa sạch khi hết phiên
// hoặc đăng xuất — không đụng tới API riêng tư của Pinia.
const resets = new Set<() => void>()

export function registerReset(fn: () => void): () => void {
  resets.add(fn)
  return () => resets.delete(fn)
}

export function runResets(): void {
  resets.forEach((fn) => fn())
}
