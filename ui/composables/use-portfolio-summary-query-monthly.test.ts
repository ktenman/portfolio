import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { focusManager } from '@tanstack/vue-query'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { usePortfolioSummaryQuery } from './use-portfolio-summary-query'
import { portfolioSummaryService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
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

type Answer = PortfolioSummaryDto[] | Error | Promise<PortfolioSummaryDto[]>

const MAX_ROWS = [row('2025-11-29', 1), row('2025-11-30', 2), row('2025-12-01', 3)]

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

const maxCalls = () =>
  vi.mocked(portfolioSummaryService.getSeries).mock.calls.filter(([range]) => range === 'MAX')

const mockSeries = (sixMonths: PortfolioSummaryDto[], ...history: Answer[]) => {
  let call = 0
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
    if (range === '6M') return sixMonths
    if (range !== 'MAX') return []
    const answer = history[Math.min(call, history.length - 1)]
    call += 1
    if (answer instanceof Error) throw answer
    return answer
  })
}

const mockHistory = (...history: Answer[]) =>
  mockSeries([row('2025-11-29', 10), row('2025-12-01', 30)], ...history)

const deferred = () => {
  let resolve: (rows: PortfolioSummaryDto[]) => void = () => {}
  const promise = new Promise<PortfolioSummaryDto[]>(done => {
    resolve = done
  })
  return { promise, resolve }
}

const datesOf = (rows: PortfolioSummaryDto[] | undefined) => rows?.map(summary => summary.date)

const failHistory = async () => {
  mockHistory(new Error('down'), new Error('down'), MAX_ROWS)
  const setup = setupQuery(ref(['LHV']), ref(true))
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
    mockHistory(MAX_ROWS)
  })

  afterEach(() => {
    focusManager.setFocused(undefined)
    vi.useRealTimers()
  })

  it('does not request MAX history while Daily is shown', async () => {
    setupQuery(ref(['LHV']), ref(false))
    await flushPromises()

    expect(maxCalls()).toHaveLength(0)
  })

  it('requests MAX history once when Monthly is on', async () => {
    const isMonthly = ref(false)
    const { queryResult } = setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()

    isMonthly.value = true
    await flushPromises()

    expect([maxCalls().length, queryResult.historyStatus.value]).toEqual([1, 'success'])
  })

  it('reports loading until MAX history arrives', async () => {
    mockHistory(new Promise(() => {}))
    const { queryResult } = setupQuery(ref(['LHV']), ref(true))
    await flushPromises()

    expect([queryResult.historyStatus.value, queryResult.monthlyRows.value]).toEqual([
      'loading',
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
      queryResult.historyStatus.value,
      queryResult.monthlyRows.value,
      maxCalls().length,
    ]).toEqual(['error', undefined, 2])
  })

  it('retries MAX history only when asked', async () => {
    const { queryResult } = await failHistory()
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await flushPromises()
    const afterFocus = maxCalls().length

    queryResult.retryHistory()
    await flushPromises()

    expect([afterFocus, maxCalls().length]).toEqual([2, 3])
  })

  it('keeps the last MAX rows when a refresh fails', async () => {
    mockHistory(MAX_ROWS, new Error('down'), new Error('down'))
    const { queryResult, queryClient } = setupQuery(ref(['LHV']), ref(true))
    await flushPromises()

    queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect([queryResult.historyStatus.value, datesOf(queryResult.monthlyRows.value)]).toEqual([
      'error',
      ['2025-11-29', '2025-11-30', '2025-12-01', '2025-12-02'],
    ])
  })

  it('refetches MAX history on recalculation only while Monthly is on', async () => {
    const isMonthly = ref(true)
    const { queryClient } = setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    const whileMonthly = maxCalls().length

    isMonthly.value = false
    await flushPromises()
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()

    expect([whileMonthly, maxCalls().length]).toEqual([2, 2])
  })

  it('reuses cached MAX history when Monthly is turned on again', async () => {
    const isMonthly = ref(true)
    setupQuery(ref(['LHV']), isMonthly)
    await flushPromises()

    isMonthly.value = false
    await flushPromises()
    isMonthly.value = true
    await flushPromises()

    expect(maxCalls()).toHaveLength(1)
  })

  it('ignores a late MAX response for the previous platform selection', async () => {
    const lhv = deferred()
    const ibkr = deferred()
    mockHistory(lhv.promise, ibkr.promise)
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

    expect([pending, datesOf(queryResult.monthlyRows.value), maxCalls()]).toEqual([
      undefined,
      ['2025-11-29', '2025-11-30', '2025-12-01', '2025-12-02'],
      [
        ['MAX', ['LHV']],
        ['MAX', ['IBKR']],
      ],
    ])
  })
})
