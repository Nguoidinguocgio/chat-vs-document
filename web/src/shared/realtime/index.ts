// Kết nối thời gian thực (T11, T16) — nơi duy nhất được dùng EventSource (luật R-3).
export { connectWorkspaceEvents, reconnectDelay, EVENT_TYPES, type WorkspaceEvent, type ConnectOptions } from './workspaceEvents'
export { SseParser, type SseMessage } from './sseParser'
