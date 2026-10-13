import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { usePortfolioSummaryQuery } from './use-portfolio-summary-query'
import { portfolioSummaryService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
import { createPortfolioSummaryDto } from '../tests/fixtures'
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

const RETRY_DELAY = 1000

const row = (date: string, totalValue = 1): PortfolioSummaryDto =>
  createPortfolioSummaryDto({ date, totalValue })

const setupQuery = (platforms?: Ref<string[]>) => {
  let queryResult: ReturnType<typeof usePortfolioSummaryQuery> | null = null
  const wrapper = renderWithProviders({
    setup() {
      queryResult = usePortfolioSummaryQuery(platforms)
      return {}
    },
    template: '<div></div>',
  })
  return { queryResult: queryResult!, queryClient: wrapper.queryClient }
}

const maxCalls = () =>
  vi.mocked(portfolioSummaryService.getSeries).mock.calls.filter(([range]) => range === 'MAX')

const mockHistory = (...answers: (PortfolioSummaryDto[] | Error)[]) => {
  let call = 0
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
    if (range === '6M') return [row('2023-12-29', 2), row('2023-12-30', 2)]
    if (range !== 'MAX') return []
    const answer = answers[Math.min(call, answers.length - 1)]
    call += 1
    if (answer instanceof Error) throw answer
    return answer
  })
}

type SummaryQuery = ReturnType<typeof usePortfolioSummaryQuery>

const press = async (query: SummaryQuery) => {
  query.loadCalendarHistory()
  await flushPromises()
}

const failTwice = async () => {
  await vi.advanceTimersByTimeAsync(RETRY_DELAY)
  await flushPromises()
}

const valuesOf = (rows: PortfolioSummaryDto[] | undefined) =>
  rows?.map(summary => [summary.date, summary.totalValue])

describe('full summary history', () => {
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
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(row('2023-12-31', 3))
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('is idle before it is asked for, loading while it is fetched and then a success', async () => {
    let answer: (rows: PortfolioSummaryDto[]) => void = () => {}
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(range =>
      range === 'MAX' ? new Promise(resolve => (answer = resolve)) : Promise.resolve([])
    )
    const { queryResult } = setupQuery()
    await flushPromises()
    const before = queryResult.historyStatus.value
    await press(queryResult)
    const during = [queryResult.historyStatus.value, queryResult.monthlyRows.value]

    answer([row('2023-06-01')])
    await flushPromises()

    expect([before, during, queryResult.historyStatus.value]).toEqual([
      'idle',
      ['loading', undefined],
      'success',
    ])
  })

  it('merges the full history, the recent rows and the current row by date', async () => {
    mockHistory([
      row('2023-06-01'),
      row('2023-12-28'),
      row('2023-12-29'),
      row('2023-12-30'),
      row('2023-12-31'),
    ])
    const { queryResult } = setupQuery()
    await flushPromises()

    await press(queryResult)

    expect(valuesOf(queryResult.monthlyRows.value)).toEqual([
      ['2023-06-01', 1],
      ['2023-12-28', 1],
      ['2023-12-29', 2],
      ['2023-12-30', 2],
      ['2023-12-31', 3],
    ])
  })

  it('reports an error after the retry and asks again on the next request', async () => {
    mockHistory(new Error('down'), new Error('down'), [row('2023-06-01')])
    const { queryResult } = setupQuery()
    await flushPromises()
    await press(queryResult)
    await failTwice()
    const failed = [
      maxCalls().length,
      queryResult.historyStatus.value,
      queryResult.monthlyRows.value,
    ]

    await press(queryResult)

    expect([failed, maxCalls().length, queryResult.historyStatus.value]).toEqual([
      [2, 'error', undefined],
      3,
      'success',
    ])
  })

  it('treats an empty full history as a success holding the recent rows', async () => {
    mockHistory([])
    const { queryResult } = setupQuery()
    await flushPromises()

    await press(queryResult)

    expect([
      queryResult.historyStatus.value,
      valuesOf(queryResult.monthlyRows.value)?.length,
      queryResult.isHistoryStale.value,
    ]).toEqual(['success', 3, false])
  })

  it('holds nothing for a platform selection that was not asked for yet', async () => {
    mockHistory([row('2023-06-01')])
    const platforms = ref(['LHV'])
    const { queryResult } = setupQuery(platforms)
    await flushPromises()
    await press(queryResult)

    platforms.value = ['SWEDBANK']
    await flushPromises()

    expect([queryResult.historyStatus.value, queryResult.monthlyRows.value]).toEqual([
      'idle',
      undefined,
    ])
  })

  it('replaces the rows when the summary is recalculated', async () => {
    mockHistory([row('2023-06-01', 1)], [row('2023-06-01', 9)])
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    await press(queryResult)

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()

    expect(valuesOf(queryResult.monthlyRows.value)?.[0]).toEqual(['2023-06-01', 9])
  })

  it('keeps the rows and turns stale when asking again fails', async () => {
    mockHistory([row('2023-06-01')], new Error('down'), new Error('down'), [row('2023-06-01', 9)])
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    await press(queryResult)
    queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    await failTwice()
    const kept = [
      queryResult.historyStatus.value,
      queryResult.isHistoryStale.value,
      valuesOf(queryResult.monthlyRows.value)?.[0],
    ]

    await press(queryResult)

    expect([kept, queryResult.isHistoryStale.value, valuesOf(queryResult.monthlyRows.value)?.[0]])
      .toEqual([['success', true, ['2023-06-01', 1]], false, ['2023-06-01', 9]])
  })
})
