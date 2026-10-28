// API giả bằng MSW theo contracts/openapi.yaml (T06). Dùng chung cho dev (`npm run dev:mock`), Vitest và Playwright.
export { handlers, mockState, resetMockState } from './handlers'
export { SCENARIOS, getScenario, setScenario, initScenarioFromUrl, type Scenario } from './scenario'
export { MOCK_PASSWORD, MOCK_USERS, LOCKED_EMAIL, DISABLED_EMAIL } from './data'
