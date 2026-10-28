import { setupServer } from 'msw/node'
import { handlers } from './index'

/** MSW cho Vitest (Node). */
export const server = setupServer(...handlers)
