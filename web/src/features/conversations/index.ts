// API công khai của feature conversations (T15). Feature khác chỉ được import từ file này (luật R-2).
export { default as ConversationSidebar } from './ConversationSidebar.vue'
export { CONVERSATIONS_KEY, conversationsApi, groupConversations, groupOf, useConversations, type ConversationGroup } from './useConversations'
