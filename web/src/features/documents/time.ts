import { formatDateTime } from '@/shared/lib/format'
import { t } from '@/shared/lib/i18n'

/** "09:02 hôm nay" · "Hôm qua" · "dd/MM/yyyy" theo giờ Việt Nam (T11 mục 3.3). */
export function relativeDay(iso: string, now: Date = new Date()): string {
  const [date, time] = formatDateTime(iso).split(' ')
  const today = formatDateTime(now).split(' ')[0]
  const yesterday = formatDateTime(new Date(now.getTime() - 86_400_000)).split(' ')[0]
  if (date === today) return t('documents.today_at', { time })
  if (date === yesterday) return t('documents.yesterday')
  return date
}
