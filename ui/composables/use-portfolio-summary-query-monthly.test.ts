import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { focusManager } from '@tanstack/vue-query'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { usePortfolioSummaryQuery } from './use-portfolio-summary-query'
import { portfolioSummaryService } from '../services/api'
import { deferred, renderWithProviders } from '../tests/test-utils'
import { createCalendarRow as row } from '../tests/fixtures'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'

vi.mock('../services/api')
vi.mock('./use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))

enableAutoUnmount(afterEach)

const MAX_ROWS = [row('2025-11-29', 1), row('2025-11-30', 2), row('2025-12-01', 3)]
const SIX_MONTHS = [row('2025-11-29', 10), row('2025-12-01', 30)]

const history = vi.fn<(range: string, platforms?: string[]) => Promise<PortfolioSummaryDto[]>>()

const setupQuery = (platforms: Ref<string[]>, isMonthly: Ref<boolean>) => {
  let queryResult: ReturnType<typeof usePortfolioSummaryQuery> | null = null
  const wrapper = renderWithProviders({
    setup() {
      queryResult = usePortfolioSummaryQuery(platforms, undefined, isMonthly)
      return {}
    },
    template: '<div></div>',
  })
  return { queryResult: queryResult!, queryClient: wrapper.queryClient }
}

const datesOf = (rows: PortfolioSummaryDto[] | undefined) => rows?.map(summary => summary.date)

const failHistory = async () => {
  history.mockRejectedValueOnce(new Error('down')).mockRejectedValueOnce(new Error('down'))
  const setup = setupQuery(ref(['LHV']), ref(true))
  await flushPromises()
  await vi.advanceTimersByTimeAsync(1000)
  await flushPromises()
  return setup
}

const failRefresh = async () => {
  history.mockResolvedValueOnce(MAX_ROWS).mockRejectedValue(new Error('down'))
  const setup = setupQuery(ref(['LHV']), ref(true))
  await flushPromises()
  setup.queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
  await flushPromises()
  await vi.advanceTimersByTimeAsync(1000)
  await flushPromises()
  return setup
}

describe('usePortfolioSummaryQuery monthly rows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 30,
      number: 0,
    })
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(row('2025-12-02', 40))
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
    history.mockReset().mockResolvedValue(MAX_ROWS)
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async (range, platforms) => {
      if (range === 'MAX') return history(range, platforms)
      return range === '6M' ? SIX_MONTHS : []
    })
  })

  afterEach(() => {
    focusManager.setFocused(undefined)
    vi.useRealTimers()
  })

  it('does not request MAX history while Daily is shown', async () => {
    setupQuery(ref(['LHV']), ref(false))
    await flushPromises()

    expect(history).not.toHaveBeenCalled()
  })

  it('requests MAX history once when Monthly is on', async () => {
    const isMonthly = ref(false)
    const { queryResult } = setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()

    isMonthly.value = true
    await flushPromises()

    expect([history.mock.calls.length, queryResult.historyError.value]).toEqual([1, null])
  })

  it('has no monthly rows until MAX history arrives', async () => {
    history.mockReturnValue(new Promise(() => {}))
    const { queryResult } = setupQuery(ref(['LHV']), ref(true))
    await flushPromises()

    expect([queryResult.historyError.value, queryResult.monthlyRows.value]).toEqual([
      null,
      undefined,
    ])
  })

  it('merges MAX, recent and current rows by date', async () => {
    const { queryResult } = setupQuery(ref(['LHV']), ref(true))
    await flushPromises()

    const rows = queryResult.monthlyRows.value
    expect([datesOf(rows), rows?.map(summary => summary.totalProfit)]).toEqual([
      ['2025-11-29', '2025-11-30', '2025-12-01', '2025-12-02'],
      [10, 2, 30, 40],
    ])
  })

  it('reports an error after the one retry when MAX history fails', async () => {
    const { queryResult } = await failHistory()

    expect([
      queryResult.historyError.value?.message,
      queryResult.monthlyRows.value,
      history.mock.calls.length,
    ]).toEqual(['down', undefined, 2])
  })

  it('retries MAX history only when asked', async () => {
    const { queryResult } = await failHistory()
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await flushPromises()
    const afterFocus = history.mock.calls.length

    queryResult.retryHistory()
    await flushPromises()

    expect([afterFocus, history.mock.calls.length]).toEqual([2, 3])
  })

  it('keeps the last MAX rows when a refresh fails', async () => {
    const { queryResult } = await failRefresh()

    expect([
      queryResult.historyError.value?.message,
      datesOf(queryResult.monthlyRows.value),
    ]).toEqual(['down', ['2025-11-29', '2025-11-30', '2025-12-01', '2025-12-02']])
  })

  it('joins a running MAX retry instead of starting another', async () => {
    const { queryResult } = await failRefresh()
    history.mockReturnValue(new Promise(() => {}))
    const failed = history.mock.calls.length

    queryResult.retryHistory()
    queryResult.retryHistory()
    await flushPromises()

    expect(history.mock.calls.length - failed).toBe(1)
  })

  it('refetches MAX history on recalculation only while Monthly is on', async () => {
    const isMonthly = ref(true)
    const { queryClient } = setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    const whileMonthly = history.mock.calls.length

    isMonthly.value = false
    await flushPromises()
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()

    expect([whileMonthly, history.mock.calls.length]).toEqual([2, 2])
  })

  it('reuses cached MAX history when Monthly is turned on again', async () => {
    const isMonthly = ref(true)
    setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()

    isMonthly.value = false
    await flushPromises()
    isMonthly.value = true
    await flushPromises()

    expect(history).toHaveBeenCalledTimes(1)
  })

  it('ignores a late MAX response for the previous platform selection', async () => {
    const lhv = deferred<PortfolioSummaryDto[]>()
    const ibkr = deferred<PortfolioSummaryDto[]>()
    history.mockReturnValueOnce(lhv.promise).mockReturnValue(ibkr.promise)
    const platforms = ref(['LHV'])
    const { queryResult } = setupQuery(platforms, ref(true))
    await flushPromises()
    platforms.value = ['IBKR']
    await flushPromises()
    const pending = queryResult.monthlyRows.value

    ibkr.resolve([row('2025-11-30', 7)])
    await flushPromises()
    lhv.resolve(MAX_ROWS)
    await flushPromises()

    expect([pending, datesOf(queryResult.monthlyRows.value), history.mock.calls]).toEqual([
      undefined,
      ['2025-11-29', '2025-11-30', '2025-12-01', '2025-12-02'],
      [
        ['MAX', ['LHV']],
        ['MAX', ['IBKR']],
      ],
    ])
  })
})
