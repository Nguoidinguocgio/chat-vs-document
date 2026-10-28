import { reactive } from 'vue'

export interface ToastItem {
  id: number
  text: string
  kind: 'info' | 'error'
}

const MAX_TOASTS = 3
const AUTO_HIDE_MS = 5000

export const toasts = reactive<ToastItem[]>([])
let nextId = 1

export function dismissToast(id: number) {
  const i = toasts.findIndex((t) => t.id === id)
  if (i >= 0) toasts.splice(i, 1)
}

function push(text: string, kind: ToastItem['kind']) {
  const item: ToastItem = { id: nextId++, text, kind }
  toasts.push(item)
  while (toasts.length > MAX_TOASTS) toasts.shift()
  if (kind === 'info') setTimeout(() => dismissToast(item.id), AUTO_HIDE_MS)
}

export const toast = {
  show: (text: string) => push(text, 'info'),
  error: (text: string) => push(text, 'error'),
}
