// Ca vi phạm có chủ đích cho từng luật (T01 mục 5). Không build, không lint trực tiếp:
// lint.test.ts tách theo dòng "@case <tên> <đường dẫn ảo> <luật mong đợi | ok>" rồi lint từng ca tại đường dẫn đó.
/* eslint-disable */

// @case R-1 src/shared/lib/x.ts boundaries/element-types
import { } from '@/features/chat'

// @case R-1-app src/shared/ui/x.ts boundaries/element-types
import { router } from '@/app/router'
export const r = router

// @case R-2 src/features/chat/x.ts boundaries/entry-point
import { } from '@/features/documents/upload'

// @case R-2-ok src/features/chat/x.ts ok
import { } from '@/features/documents'

// @case R-3-fetch src/features/chat/x.ts no-restricted-globals
export const a = fetch('/api/x')

// @case R-3-es src/shared/api/x.ts no-restricted-globals
export const b = new EventSource('/api/events')

// @case R-3-xhr src/features/chat/x.ts no-restricted-globals
export const c = new XMLHttpRequest()

// @case R-3-ok-api src/shared/api/x.ts ok
export const d = fetch('/api/x')

// @case R-3-ok-realtime src/shared/realtime/x.ts ok
export const e = new EventSource('/api/events')

// @case R-3-ok-upload src/features/documents/upload/x.ts ok
export const f = new XMLHttpRequest()

// @case R-4 src/features/chat/x.ts no-restricted-syntax
document.body.innerHTML = '<b>x</b>'

// @case R-4-ok src/shared/lib/markdown/x.ts ok
document.body.innerHTML = '<b>x</b>'

// @case R-5-static src/features/source-viewer/x.ts no-restricted-imports
import * as pdfjs from 'pdfjs-dist'
export const p = pdfjs

// @case R-5-dynamic src/features/chat/x.ts no-restricted-syntax
export const q = () => import('pdfjs-dist')

// @case R-5-ok src/features/source-viewer/x.ts ok
export const s = () => import('pdfjs-dist')

// @case R-6-import src/features/chat/x.ts no-restricted-imports
import { } from '@/shared/lib/analytics/transport'

// @case R-6-beacon src/features/chat/x.ts no-restricted-properties
navigator.sendBeacon('/api/analytics', '{}')

// @case token src/features/auth/x.ts no-restricted-syntax
localStorage.setItem('access_token', 'x')

// @case token-ok src/features/chat/x.ts ok
sessionStorage.setItem('draft', 'x')

// @case hex src/features/chat/x.ts no-restricted-syntax
export const color = '#1d4ed8'

// @case reka src/features/chat/x.ts no-restricted-imports
import { DialogRoot } from 'reka-ui'
export const dr = DialogRoot
