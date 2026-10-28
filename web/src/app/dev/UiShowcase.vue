<script setup lang="ts">
import { ref } from 'vue'
import {
  Alert, Badge, Button, Dialog, EmptyState, IconButton, Input, Menu, Popover, ProgressBar, Skeleton, Spinner, Textarea, Tooltip, toast,
} from '@/shared/ui'

const dialogOpen = ref(false)
const destructiveOpen = ref(false)
const name = ref('')
const note = ref('')
const menuItems = [
  { key: 'profile', label: 'Tài khoản' },
  { key: 'logout', label: 'Đăng xuất' },
]
</script>

<template>
  <main class="mx-auto flex max-w-4xl flex-col gap-8 p-8">
    <h1 class="text-xl font-semibold">Thư viện UI (/dev/ui)</h1>

    <section aria-labelledby="s-btn" class="flex flex-col gap-3">
      <h2 id="s-btn" class="text-lg font-semibold">Button</h2>
      <div class="flex flex-wrap items-center gap-2">
        <Button>Tải lên</Button>
        <Button variant="secondary">Hủy</Button>
        <Button variant="danger">Xóa</Button>
        <Button variant="ghost">Thử lại</Button>
        <Button size="sm">Nhỏ</Button>
        <Button loading>Đang xử lý</Button>
        <Button disabled>Vô hiệu</Button>
      </div>
      <div class="flex items-center gap-2">
        <Tooltip text="Đóng"><IconButton label="Đóng">✕</IconButton></Tooltip>
        <IconButton label="Vô hiệu" disabled>✕</IconButton>
      </div>
    </section>

    <section aria-labelledby="s-form" class="flex max-w-md flex-col gap-3">
      <h2 id="s-form" class="text-lg font-semibold">Input / Textarea</h2>
      <Input v-model="name" label="Tên tài liệu" placeholder="Điều khoản thanh toán" />
      <Input model-value="" label="Email" error="Email không hợp lệ" />
      <Textarea v-model="note" label="Ghi chú" />
      <Textarea model-value="" label="Câu hỏi" error="Câu hỏi quá dài" />
      <Input model-value="Không sửa được" label="Vô hiệu" disabled />
    </section>

    <section aria-labelledby="s-badge" class="flex flex-col gap-3">
      <h2 id="s-badge" class="text-lg font-semibold">Badge</h2>
      <div class="flex flex-wrap gap-2">
        <Badge>Trung tính</Badge><Badge tone="success">Sẵn sàng</Badge><Badge tone="warning">Đang xử lý</Badge>
        <Badge tone="danger">Lỗi</Badge><Badge tone="info">Mới</Badge>
      </div>
    </section>

    <section aria-labelledby="s-alert" class="flex flex-col gap-3">
      <h2 id="s-alert" class="text-lg font-semibold">Alert</h2>
      <Alert>Thông tin chung.</Alert>
      <Alert tone="warning">Có 3 trang không đọc được.</Alert>
      <Alert tone="error">
        Không thể xử lý tài liệu.
        <template #action><Button size="sm" variant="secondary">Thử lại</Button></template>
      </Alert>
    </section>

    <section aria-labelledby="s-prog" class="flex max-w-md flex-col gap-3">
      <h2 id="s-prog" class="text-lg font-semibold">Spinner / Skeleton / ProgressBar</h2>
      <Spinner label="Đang tải" />
      <Skeleton width="60%" />
      <ProgressBar :value="0" label="Chờ tải lên" />
      <ProgressBar :value="65" label="Tiến trình tải lên" />
      <ProgressBar :value="100" label="Đã tải lên" />
    </section>

    <section aria-labelledby="s-empty" class="flex flex-col gap-3">
      <h2 id="s-empty" class="text-lg font-semibold">EmptyState</h2>
      <EmptyState title="Chưa có tài liệu" description="Tải lên tệp PDF đầu tiên để bắt đầu đặt câu hỏi.">
        <Button>Tải lên</Button>
      </EmptyState>
    </section>

    <section aria-labelledby="s-ovl" class="flex flex-col gap-3">
      <h2 id="s-ovl" class="text-lg font-semibold">Dialog / Menu / Popover / Toast</h2>
      <div class="flex flex-wrap gap-2">
        <Button variant="secondary" @click="dialogOpen = true">Mở hộp thoại</Button>
        <Button variant="danger" @click="destructiveOpen = true">Xóa tài liệu…</Button>
        <Menu :items="menuItems" @select="(k) => toast.show(`Chọn: ${k}`)">
          <template #trigger><Button variant="secondary">Menu</Button></template>
        </Menu>
        <Popover>
          <template #trigger><Button variant="secondary">Popover</Button></template>
          <p>Trang 4 · Điều khoản thanh toán</p>
        </Popover>
        <Button variant="ghost" @click="toast.show('Đã lưu thay đổi')">Toast thông tin</Button>
        <Button variant="ghost" @click="toast.error('Tải lên thất bại')">Toast lỗi</Button>
      </div>
      <Dialog v-model:open="dialogOpen" title="Xác nhận" description="Bạn có muốn tiếp tục?" confirm-label="Tiếp tục" cancel-label="Hủy" />
      <Dialog v-model:open="destructiveOpen" destructive title="Xóa tài liệu" description="Hành động này không thể hoàn tác." confirm-label="Xóa" cancel-label="Hủy" />
    </section>
  </main>
</template>
