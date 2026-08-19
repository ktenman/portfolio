import { describe, it, expect, vi, beforeEach } from 'vitest'
import { httpClient } from '../utils/http-client'
import { transactionsService } from './transactions-service'
import { instrumentsService } from './instruments-service'
import { portfolioSummaryService } from './portfolio-summary-service'
import { diversificationService } from './diversification-service'
import { utilityService } from './utility-service'
import { TimeRange } from '../models/generated/domain-models'
import type { AllocationDto } from '../models/generated/domain-models'
import type { CachedState } from '../components/diversification/types'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const PLATFORMS = ['TRADING212', 'LIGHTYEAR']
const ALLOCATIONS: AllocationDto[] = [{ instrumentId: 7, percentage: 100 }]
const CONFIG = { allocations: ALLOCATIONS } as unknown as CachedState

describe('api services', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(httpClient.get).mockResolvedValue({} as never)
    vi.mocked(httpClient.post).mockResolvedValue({} as never)
    vi.mocked(httpClient.put).mockResolvedValue({} as never)
  })

  it.each([
    [
      'transactions.getAll',
      () => transactionsService.getAll(PLATFORMS, '2026-01-01', '2026-02-01'),
      'get',
      '/transactions',
      { params: { platforms: PLATFORMS, fromDate: '2026-01-01', untilDate: '2026-02-01' } },
    ],
    [
      'transactions.getAll bare',
      () => transactionsService.getAll(),
      'get',
      '/transactions',
      { params: {} },
    ],
    [
      'transactions.getPlatforms',
      () => transactionsService.getPlatforms(),
      'get',
      '/transactions/platforms',
      undefined,
    ],
    [
      'instruments.getAll',
      () => instrumentsService.getAll(PLATFORMS, TimeRange.ONE_WEEK),
      'get',
      '/instruments',
      { params: { period: TimeRange.ONE_WEEK, platforms: PLATFORMS } },
    ],
    [
      'instruments.getAll bare',
      () => instrumentsService.getAll(),
      'get',
      '/instruments',
      { params: { period: TimeRange.ONE_DAY } },
    ],
    [
      'instruments.create',
      () => instrumentsService.create({ symbol: 'AAPL' }),
      'post',
      '/instruments',
      { symbol: 'AAPL' },
    ],
    [
      'instruments.update',
      () => instrumentsService.update(7, { symbol: 'AAPL' }),
      'put',
      '/instruments/7',
      { symbol: 'AAPL' },
    ],
    [
      'instruments.refreshPrices',
      () => instrumentsService.refreshPrices(),
      'post',
      '/instruments/refresh-prices',
      undefined,
    ],
    [
      'summary.getHistorical',
      () => portfolioSummaryService.getHistorical(2, 25, PLATFORMS),
      'get',
      '/portfolio-summary/historical',
      { params: { page: 2, size: 25, platforms: PLATFORMS } },
    ],
    [
      'summary.getHistorical without platforms',
      () => portfolioSummaryService.getHistorical(0, 10, []),
      'get',
      '/portfolio-summary/historical',
      { params: { page: 0, size: 10 } },
    ],
    [
      'summary.getCurrent',
      () => portfolioSummaryService.getCurrent(PLATFORMS),
      'get',
      '/portfolio-summary/current',
      { params: { platforms: PLATFORMS } },
    ],
    [
      'summary.getCurrent without platforms',
      () => portfolioSummaryService.getCurrent([]),
      'get',
      '/portfolio-summary/current',
      { params: {} },
    ],
    [
      'summary.getSeries',
      () => portfolioSummaryService.getSeries(TimeRange.ONE_YEAR, PLATFORMS),
      'get',
      '/portfolio-summary/series',
      { params: { range: TimeRange.ONE_YEAR, platforms: PLATFORMS } },
    ],
    [
      'summary.getRangeChange',
      () => portfolioSummaryService.getRangeChange(TimeRange.ONE_MONTH),
      'get',
      '/portfolio-summary/range-change',
      { params: { range: TimeRange.ONE_MONTH } },
    ],
    [
      'summary.getXirrWindows',
      () => portfolioSummaryService.getXirrWindows(PLATFORMS),
      'get',
      '/portfolio-summary/xirr-windows',
      { params: { platforms: PLATFORMS } },
    ],
    [
      'summary.getAnnualWindows',
      () => portfolioSummaryService.getAnnualWindows(),
      'get',
      '/portfolio-summary/annual-windows',
      { params: {} },
    ],
    [
      'diversification.getAvailableEtfs',
      () => diversificationService.getAvailableEtfs(),
      'get',
      '/diversification/available-etfs',
      undefined,
    ],
    [
      'diversification.calculate',
      () => diversificationService.calculate(ALLOCATIONS),
      'post',
      '/diversification/calculate',
      { allocations: ALLOCATIONS },
    ],
    [
      'diversification.saveConfig',
      () => diversificationService.saveConfig(CONFIG),
      'put',
      '/diversification/config',
      CONFIG,
    ],
    [
      'utility.getCalculationResult',
      () => utilityService.getCalculationResult(),
      'get',
      '/calculator',
      undefined,
    ],
    ['utility.getBuildInfo', () => utilityService.getBuildInfo(), 'get', '/build-info', undefined],
  ])('%s calls the right endpoint', async (_name, call, method, url, payload) => {
    await call()

    const mock = vi.mocked(httpClient[method as 'get' | 'post' | 'put'])
    expect(mock).toHaveBeenCalledWith(...(payload === undefined ? [url] : [url, payload]))
  })

  it('sends recalculate with an extended timeout and no body', async () => {
    await portfolioSummaryService.recalculate()

    expect(httpClient.post).toHaveBeenCalledWith('/portfolio-summary/recalculate', undefined, {
      timeout: 60000,
    })
  })

  it('resolves getConfig to null when the request fails', async () => {
    vi.mocked(httpClient.get).mockRejectedValueOnce(new Error('404'))

    await expect(diversificationService.getConfig()).resolves.toBeNull()
  })

  it('builds a proxied logo url from a uuid', () => {
    expect(utilityService.getLogoUrl('550e8400-e29b-41d4-a716-446655440000')).toBe(
      '/api/logos/550e8400-e29b-41d4-a716-446655440000'
    )
  })

  it('propagates request errors to the caller', async () => {
    const failure = new Error('Network error')
    vi.mocked(httpClient.get).mockRejectedValueOnce(failure)

    await expect(utilityService.getCalculationResult()).rejects.toThrow(failure)
  })
})
