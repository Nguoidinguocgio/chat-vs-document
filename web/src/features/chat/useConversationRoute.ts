import { onBeforeUnmount, watch, type Ref } from 'vue'
import { useAnswerStream } from './useAnswerStream'

/**
 * `/c` = hội thoại mới (chỉ tạo ở máy chủ khi gửi câu hỏi đầu tiên); `/c/:id` = tải toàn bộ tin
 * nhắn. Đổi hội thoại hoặc rời trang → hủy luồng đang chạy.
 */
export function useConversationRoute(routeId: Ref<string | null>) {
  const chat = useAnswerStream()
  watch(routeId, (id) => void chat.open(id), { immediate: true })
  onBeforeUnmount(() => chat.abort('leave'))
  return chat
}
