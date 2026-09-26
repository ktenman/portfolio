import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const NOW = '2026-09-25T10:00:00Z'

const collection = (provider: string, operation: string, overrides: object = {}) => ({
  key: `${provider}_${operation}`.toUpperCase(),
  provider,
  operation,
  job: `${provider}${operation}Job`,
  status: 'OK',
  expected: 12,
  fetched: 12,
  persisted: 12,
  failed: 0,
  failedItems: [],
  items: [],
  consecutiveEmptyRuns: 0,
  durationSeconds: 2.4,
  lastAttempt: '2026-09-25T09:58:00Z',
  lastCompletion: '2026-09-25T09:58:02Z',
  lastFullSuccess: '2026-09-25T09:58:00Z',
  deadline: '2026-09-25T10:05:00Z',
  breakerOpen: false,
  ...overrides,
})

const COLLECTIONS = [
  collection('lightyear', 'prices'),
  collection('trading212', 'prices', {
    persisted: 10,
    failed: 2,
    failedItems: ['AAPL', 'NVDA'],
    status: 'PARTIAL_FAILURE',
  }),
  collection('binance', 'prices', { expected: 3, fetched: 3, persisted: 3 }),
  collection('ft', 'history', {
    lastFullSuccess: '2026-09-24T22:10:00Z',
    deadline: '2026-09-25T23:30:00Z',
  }),
  collection('lightyear', 'history', {
    status: 'OVERDUE',
    lastFullSuccess: '2026-09-23T22:10:00Z',
  }),
  collection('lightyear', 'holdings', { durationSeconds: 41.7 }),
  collection('trading212', 'holdings', {
    status: 'BREAKER_OPEN',
    breakerOpen: true,
    consecutiveEmptyRuns: 3,
  }),
  collection('blackrock', 'holdings', { expected: 1, fetched: 1, persisted: 1 }),
  collection('vanguard', 'holdings', { status: 'DISABLED', lastFullSuccess: null }),
]

export const stubMonitoring: RouteStub = async page => {
  await page.clock.setFixedTime(NOW)
  await page.route(apiRoute(`${API_ENDPOINTS.MONITORING_COLLECTIONS}/stream`), route =>
    route.fulfill({
      contentType: 'text/event-stream',
      body: `data: ${JSON.stringify(COLLECTIONS)}\n\n`,
    })
  )
}
