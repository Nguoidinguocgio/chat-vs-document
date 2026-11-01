# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: a11y\TC-A11Y-01.spec.ts >> TC-A11Y-01 axe-core ở mọi trạng thái chính
- Location: tests\a11y\TC-A11Y-01.spec.ts:12:5

# Error details

```
Error: [
 {
  "n": 1,
  "status": "failed",
  "screenshot": "screenshots/01-axe-auth-empty.png",
  "note": "vi phạm serious/critical | expect(received).toEqual(expected) // deep equality | - Expected  - 1"
 },
 {
  "n": 2,
  "status": "failed",
  "screenshot": "screenshots/02-axe-upload.png",
  "note": "expect(locator).toBeVisible() failed | Locator: getByText('Chưa có tài liệu nào dùng được') | Expected: visible"
 },
 {
  "n": 3,
  "status": "failed",
  "screenshot": "screenshots/03-axe-chat.png",
  "note": "vi phạm serious/critical | expect(received).toEqual(expected) // deep equality | - Expected  - 1"
 },
 {
  "n": 4,
  "status": "blocked",
  "screenshot": "screenshots/04-axe-dialogs.png",
  "note": "BỊ CHẶN một phần: Bấm chip [1] không mở bảng Xem nguồn — T18 (features/source-viewer) chưa implement. Các trạng thái còn lại của bước 4 (hộp thoại, 404, 403) đạt 0 serious/critical: Hộp thoại (Dialog, /dev/ui): critical 0, serious 0, moderate 0, minor 0 | Trang 404: critical 0, serious 0, moderate 0, minor 0 | Trang 403: critical 0, serious 0, moderate 0, minor 0"
 }
]

expect(received).toEqual(expected) // deep equality

- Expected  -  1
+ Received  + 20

- Array []
+ Array [
+   Object {
+     "n": 1,
+     "note": "vi phạm serious/critical | expect(received).toEqual(expected) // deep equality | - Expected  - 1",
+     "screenshot": "screenshots/01-axe-auth-empty.png",
+     "status": "failed",
+   },
+   Object {
+     "n": 2,
+     "note": "expect(locator).toBeVisible() failed | Locator: getByText('Chưa có tài liệu nào dùng được') | Expected: visible",
+     "screenshot": "screenshots/02-axe-upload.png",
+     "status": "failed",
+   },
+   Object {
+     "n": 3,
+     "note": "vi phạm serious/critical | expect(received).toEqual(expected) // deep equality | - Expected  - 1",
+     "screenshot": "screenshots/03-axe-chat.png",
+     "status": "failed",
+   },
+ ]
```

# Test source

```ts
  67  |       await expect(page.getByText('Chưa có tài liệu nào dùng được')).toBeVisible()
  68  |       await page.getByRole('button', { name: 'Mở danh sách tài liệu' }).first().click()
  69  |       await expect(page.getByText('Scan_01.pdf').first()).toBeVisible()
  70  |       await scan(xs, 'Danh sách có lỗi (all_failed)', '02b-all-failed')
  71  |       await showAxePanel(page, 'Bước 2', xs)
  72  |       assertClean(xs)
  73  |       return summary(xs)
  74  |     })
  75  |     await hideAxePanel(page)
  76  | 
  77  |     await run('axe-chat', async () => {
  78  |       const xs: AxeState[] = []
  79  |       await mockLogin(page, 'lan@congty.vn', 'answered')
  80  |       await ready(page)
  81  |       const card = await ask(page, 'Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?')
  82  |       await expect(card).toHaveAttribute('aria-busy', 'true')
  83  |       await scan(xs, 'Đang tạo câu trả lời', '03a-streaming')
  84  |       await expect(card).toHaveAttribute('data-state', 'answered', { timeout: 15_000 })
  85  |       await scan(xs, 'Có trích dẫn', '03b-answered')
  86  | 
  87  |       for (const [scenario, state, label, file] of [
  88  |         ['not_found', 'notfound', 'Không tìm thấy', '03c-not-found'],
  89  |         ['partial', 'partial', 'Một phần', '03d-partial'],
  90  |         ['llm_error', 'failed', 'Lỗi dịch vụ AI', '03e-llm-error'],
  91  |         ['interrupted', 'interrupted', 'Gián đoạn', '03f-interrupted'],
  92  |       ] as const) {
  93  |         await mockLogin(page, 'lan@congty.vn', scenario)
  94  |         await ready(page)
  95  |         const c = await ask(page, 'Câu hỏi kiểm thử khả năng tiếp cận')
  96  |         await expect(c).toHaveAttribute('data-state', state, { timeout: 15_000 })
  97  |         await scan(xs, label, file)
  98  |       }
  99  |       await showAxePanel(page, 'Bước 3', xs)
  100 |       assertClean(xs)
  101 |       return summary(xs)
  102 |     })
  103 |     await hideAxePanel(page)
  104 | 
  105 |     let sourceViewerMissing = ''
  106 |     let step4Failed = false
  107 |     await run(
  108 |       'axe-dialogs',
  109 |       async () => {
  110 |         const xs: AxeState[] = []
  111 |         // Bảng Xem nguồn (T18): bấm chip trích dẫn
  112 |         await mockLogin(page, 'lan@congty.vn', 'answered')
  113 |         await ready(page)
  114 |         const card = await ask(page, 'Thời hạn thanh toán của hợp đồng A và đề xuất B khác nhau thế nào?')
  115 |         await expect(card).toHaveAttribute('data-state', 'answered', { timeout: 15_000 })
  116 |         await card.getByRole('button', { name: /^Trích dẫn 1:/ }).click()
  117 |         const viewer = page.locator('[data-testid="source-viewer"], [role="dialog"], [role="complementary"]').filter({ hasText: 'HopDong_A.pdf' })
  118 |         if (await viewer.first().isVisible({ timeout: 2000 }).catch(() => false)) await scan(xs, 'Bảng Xem nguồn', '04a-source-viewer')
  119 |         else {
  120 |           sourceViewerMissing = 'Bấm chip [1] không mở bảng Xem nguồn — T18 (features/source-viewer) chưa implement'
  121 |           await page.screenshot({ path: `${extraDir}/04a-source-viewer-missing.png` })
  122 |         }
  123 | 
  124 |         // Hộp thoại: ứng dụng hiện chưa có hộp thoại trong luồng chính → quét component Dialog ở /dev/ui (chế độ dev)
  125 |         await page.goto('/dev/ui')
  126 |         await page.getByRole('button', { name: 'Mở hộp thoại' }).click()
  127 |         await expect(page.getByRole('dialog')).toBeVisible()
  128 |         await scan(xs, 'Hộp thoại (Dialog, /dev/ui)', '04b-dialog')
  129 |         await page.keyboard.press('Escape')
  130 | 
  131 |         await page.goto('/khong-ton-tai')
  132 |         await expect(page.getByText('404').first()).toBeVisible()
  133 |         await scan(xs, 'Trang 404', '04c-404')
  134 |         await page.goto('/403')
  135 |         await expect(page.getByText('403').first()).toBeVisible()
  136 |         await scan(xs, 'Trang 403', '04d-403')
  137 |         await showAxePanel(page, 'Bước 4', xs)
  138 |         if (xs.some((s) => blocking(s) > 0)) step4Failed = true
  139 |         assertClean(xs)
  140 |         if (sourceViewerMissing) throw new Error(sourceViewerMissing)
  141 |         return summary(xs)
  142 |       },
  143 |       async () =>
  144 |         !step4Failed && sourceViewerMissing
  145 |           ? `BỊ CHẶN một phần: ${sourceViewerMissing}. Các trạng thái còn lại của bước 4 (hộp thoại, 404, 403) đạt 0 serious/critical: ${summary(all.slice(-3))}`
  146 |           : undefined,
  147 |     )
  148 |   } finally {
  149 |     writeFileSync(`${reportDir()}/axe-report.json`, JSON.stringify({ tags: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'], states: all }, null, 2))
  150 |     const video = await saveVideo(page)
  151 |     writeResult('TC-A11Y-01', {
  152 |       status: overall(steps, 4),
  153 |       mode: 'mock',
  154 |       browser: `${browserName} ${page.context().browser()?.version()} (${testInfo.project.name})`,
  155 |       axe_totals: all.reduce(
  156 |         (acc, s) => {
  157 |           for (const k of Object.keys(acc)) acc[k] += s.counts[k]
  158 |           return acc
  159 |         },
  160 |         { critical: 0, serious: 0, moderate: 0, minor: 0 } as Record<string, number>,
  161 |       ),
  162 |       steps,
  163 |       video: video ?? null,
  164 |       defects: [],
  165 |     })
  166 |   }
> 167 |   expect(steps.filter((s) => s.status === 'failed'), JSON.stringify(steps, null, 1)).toEqual([])
      |                                                                                      ^ Error: [
  168 | })
  169 | 
```