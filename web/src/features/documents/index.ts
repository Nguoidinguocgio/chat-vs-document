// API công khai của feature documents (T10, T11). Feature khác chỉ được import từ file này (luật R-2).
export { default as DocumentsPanel } from './DocumentsPanel.vue'
export { default as EmptyWorkspaceState } from './EmptyWorkspaceState.vue'
export { default as GlobalDropOverlay } from './upload/GlobalDropOverlay.vue'
export { applyDocumentEvent, useDocuments, useDocumentsSummary, type DocumentsSummary } from './useDocuments'
export { DOCUMENTS_KEY, MAX_DOCS } from './api'
export { validateFiles } from './upload/validateFiles'
export { useUploadQueue } from './upload/queue'
export { requestFilePicker } from './upload/picker'
