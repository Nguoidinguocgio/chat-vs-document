// Định dạng hiển thị (T05 mục 3.3): giờ Việt Nam, dung lượng, rút gọn tên file ở giữa.
const dateTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/** `dd/MM/yyyy HH:mm` theo giờ Asia/Ho_Chi_Minh. */
export function formatDateTime(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso
  const p = Object.fromEntries(dateTime.formatToParts(d).map((x) => [x.type, x.value]))
  return `${p.day}/${p.month}/${p.year} ${p.hour === '24' ? '00' : p.hour}:${p.minute}`
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let v = bytes / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toLocaleString('vi-VN', { maximumFractionDigits: v < 10 ? 1 : 0 })} ${units[i]}`
}

/** Rút gọn ở giữa, giữ đuôi file: `BaoCao_Q3_2026_ban…_v3.pdf`. */
export function midEllipsis(name: string, max = 34): string {
  const chars = Array.from(name)
  if (chars.length <= max) return name
  const keep = max - 1
  const tail = Math.ceil(keep * 0.4)
  return chars.slice(0, keep - tail).join('') + '…' + chars.slice(chars.length - tail).join('')
}
