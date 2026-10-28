import { defineStore } from 'pinia'
import { registerReset } from '@/shared/lib/reset'

export type RightPanel = 'closed' | 'documents' | 'source'
export interface CitationRef {
  messageId: string
  index: number
}

/** Trạng thái bố cục: ngăn kéo thanh trái (< 1024 px) và khe bảng phải (CMP-10: Tài liệu | Xem nguồn). */
export const useLayoutStore = defineStore('layout', {
  state: () => ({ navOpen: false, panel: 'closed' as RightPanel, panelInitialized: false, citation: null as CitationRef | null }),
  actions: {
    init() {
      registerReset(() => this.$reset())
    },
    togglePanel(p: Exclude<RightPanel, 'closed'>) {
      this.panel = this.panel === p ? 'closed' : p
    },
    openPanel(p: Exclude<RightPanel, 'closed'>) {
      this.panel = p
    },
    /** Sự kiện UI `citation:open` từ chip/nguồn (T16); bảng Xem nguồn hiển thị ở T18. */
    openCitation(c: CitationRef) {
      this.citation = c
    },
    closePanel() {
      this.panel = 'closed'
    },
  },
})
