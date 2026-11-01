import { defineConfig, devices } from '@playwright/test'

// Cấu hình E2E theo docs/test/test_cases/README.md §5. Kết quả của mỗi test case nằm ở test_report/<TC_ID>/.
const tc = process.env.TC_ID ?? 'adhoc'
const reportDir = process.env.REPORT_DIR ?? `test_report/${tc}`

export default defineConfig({
  testDir: './tests',
  outputDir: `../${reportDir}/playwright-output`,
  reporter: [['list']],
  retries: 0,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    video: 'on',
    screenshot: 'off',
    trace: 'retain-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
  // API giả (MSW): frontend chạy độc lập, không cần backend.
  webServer: {
    command: 'npm run dev:mock',
    cwd: '../web',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
