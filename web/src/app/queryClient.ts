import { QueryClient } from '@tanstack/vue-query'
import { isApiError } from '@/shared/api'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // Không thử lại lỗi 4xx (phiên, quyền, không tìm thấy); lỗi mạng thử lại 1 lần.
      retry: (count, e) => count < 1 && !(isApiError(e) && e.status >= 400 && e.status < 500),
    },
  },
})
