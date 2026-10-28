import { ref } from 'vue'

/** Yêu cầu mở hộp chọn file từ nơi khác (nút "Tải lên" ở trạng thái trống); UploadDropzone lắng nghe. */
export const pickerRequests = ref(0)

export function requestFilePicker(): void {
  pickerRequests.value++
}
