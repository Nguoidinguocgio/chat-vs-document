import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import type { DocumentsSummary } from '@/features/documents'

export const MAX_QUESTION = 2000
export const COUNTER_FROM = 1800

/** Trạng thái ô nhập ST-04, theo thứ tự ưu tiên (T15 mục 3.2). */
export type ComposerCode = 'offline' | 'no_documents' | 'processing_only' | 'all_failed' | 'busy' | 'empty' | 'too_long' | 'ready'

export interface ComposerInput {
  online: boolean
  summary: Pick<DocumentsSummary, 'total' | 'hasReady' | 'processing'>
  /** Danh sách tài liệu đã tải xong; chưa tải thì không khóa ô nhập (tránh nháy, không mất ký tự gõ sớm). */
  documentsLoaded?: boolean
  busy: boolean
  text: string
}

export interface ComposerState {
  code: ComposerCode
  /** Ô nhập bị vô hiệu hóa (không gõ được). */
  locked: boolean
  /** Nút Gửi bấm được. */
  canSend: boolean
}

/** Suy ra (không lưu) trạng thái ô nhập. */
export function composerState(i: ComposerInput): ComposerState {
  const s = (code: ComposerCode, locked = false, canSend = false): ComposerState => ({ code, locked, canSend })
  if (!i.online) return s('offline')
  const loaded = i.documentsLoaded !== false
  if (loaded && i.summary.total === 0) return s('no_documents', true)
  if (loaded && !i.summary.hasReady) return i.summary.processing > 0 ? s('processing_only', true) : s('all_failed', true)
  if (i.busy) return s('busy')
  if (i.text.trim() === '') return s('empty')
  if (i.text.length > MAX_QUESTION) return s('too_long')
  return s('ready', false, true)
}

/** `navigator.onLine` dạng ref. */
export function useOnline(): Ref<boolean> {
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const update = () => (online.value = navigator.onLine)
  onMounted(() => {
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('online', update)
    window.removeEventListener('offline', update)
  })
  return online
}

export function useComposerState(text: Ref<string>, busy: Ref<boolean>, summary: Ref<DocumentsSummary>, documentsLoaded: Ref<boolean>) {
  const online = useOnline()
  return computed(() => composerState({ online: online.value, summary: summary.value, documentsLoaded: documentsLoaded.value, busy: busy.value, text: text.value }))
}
