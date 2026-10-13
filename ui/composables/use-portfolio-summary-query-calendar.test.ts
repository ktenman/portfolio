import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { usePortfolioSummaryQuery } from './use-portfolio-summary-query'
import { portfolioSummaryService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'

vi.mock('../services/api')
vi.mock('./use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))

enableAutoUnmount(afterEach)

const ONE_MINUTE = 60 * 1000
const FIVE_MINUTES = 5 * ONE_MINUTE

const row = (date: string): PortfolioSummaryDto => createPortfolioSummaryDto({ date })

const setupQuery = (platforms?: Ref<string[]>, range?: Ref<TimeRange>) => {
  let queryResult: ReturnType<typeof usePortfolioSummaryQuery> | null = null
  const wrapper = renderWithProviders({
    setup() {
      queryResult = usePortfolioSummaryQuery(platforms, range)
      return {}
    },
    template: '<div></div>',
  })
  return { queryResult: queryResult!, queryClient: wrapper.queryClient, wrapper }
}

const sixMonthCalls = () =>
  vi.mocked(portfolioSummaryService.getSeries).mock.calls.filter(([range]) => range === '6M')

const mockSeries = (...answers: (PortfolioSummaryDto[] | Error)[]) => {
  let call = 0
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
    if (range !== '6M') return []
    const answer = answers[Math.min(call, answers.length - 1)]
    call += 1
    if (answer instanceof Error) throw answer
    return answer
  })
}

const datesOf = (rows: PortfolioSummaryDto[] | undefined) => rows?.map(summary => summary.date)

const maxCalls = () =>
  vi.mocked(portfolioSummaryService.getSeries).mock.calls.filter(([range]) => range === 'MAX')

const mockHistory = (...answers: (PortfolioSummaryDto[] | Error)[]) => {
  let call = 0
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
    if (range === '6M') return [row('2023-12-29'), row('2023-12-30')]
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

const stateOf = (query: SummaryQuery) => [maxCalls().length, query.calendarHistory.value]

describe('usePortfolioSummaryQuery daily calendar rows', () => {
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
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(row('2023-12-31'))
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
    mockSeries([row('2023-12-29'), row('2023-12-30')])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('requests six months once, whatever the chart range is', async () => {
    const range = ref(TimeRange.ONE_MONTH)
    setupQuery(undefined, range)
    await flushPromises()

    range.value = TimeRange.YTD
    await flushPromises()

    expect(sixMonthCalls()).toEqual([['6M', undefined]])
  })

  it('is undefined until the first answer, then the series merged with the current row', async () => {
    const { queryResult } = setupQuery()

    const before = queryResult.dailyCalendarRows.value
    await flushPromises()

    expect(before).toBeUndefined()
    expect(datesOf(queryResult.dailyCalendarRows.value)).toEqual([
      '2023-12-29',
      '2023-12-30',
      '2023-12-31',
    ])
  })

  it('holds only the current row after a failed request, also while it is asked again', async () => {
    mockSeries(new Error('down'))
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    const afterFailure = datesOf(queryResult.dailyCalendarRows.value)
    vi.mocked(portfolioSummaryService.getSeries).mockReturnValue(new Promise(() => {}))

    queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'daily-calendar'] })
    await flushPromises()

    expect([afterFailure, datesOf(queryResult.dailyCalendarRows.value)]).toEqual([
      ['2023-12-31'],
      ['2023-12-31'],
    ])
  })

  it('reports a failed request as the calendar error, not the error of the chart range', async () => {
    mockSeries(new Error('down'))
    const { queryResult } = setupQuery()
    await flushPromises()

    expect([queryResult.calendarError.value, queryResult.rangeError.value]).toEqual(['down', null])
  })

  it('keeps the calendar error while the failed request is asked again', async () => {
    mockSeries(new Error('down'))
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    vi.mocked(portfolioSummaryService.getSeries).mockReturnValue(new Promise(() => {}))

    queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'daily-calendar'] })
    await flushPromises()

    expect(queryResult.calendarError.value).toBe('down')
  })

  it('clears the calendar error when the request succeeds after a failure', async () => {
    mockSeries(new Error('down'), [row('2023-12-29'), row('2023-12-30')])
    const { queryResult } = setupQuery()
    await flushPromises()
    const afterFailure = queryResult.calendarError.value

    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()

    expect([afterFailure, queryResult.calendarError.value]).toEqual(['down', null])
  })

  it('asks again five minutes after a failed request', async () => {
    mockSeries(new Error('down'), [row('2023-12-29'), row('2023-12-30')])
    const { queryResult } = setupQuery()
    await flushPromises()

    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()

    expect(datesOf(queryResult.dailyCalendarRows.value)).toEqual([
      '2023-12-29',
      '2023-12-30',
      '2023-12-31',
    ])
  })

  it('keeps the rows it holds when a later request fails', async () => {
    mockSeries([row('2023-12-29'), row('2023-12-30')], new Error('down'))
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'daily-calendar'] })
    await flushPromises()

    expect([sixMonthCalls().length, datesOf(queryResult.dailyCalendarRows.value)]).toEqual([
      2,
      ['2023-12-29', '2023-12-30', '2023-12-31'],
    ])
  })

  it('keeps the rows of one platform selection apart from the next', async () => {
    const platforms = ref(['LHV'])
    const { queryResult } = setupQuery(platforms)
    await flushPromises()
    vi.mocked(portfolioSummaryService.getSeries).mockReturnValue(new Promise(() => {}))

    platforms.value = ['SWEDBANK']
    await flushPromises()

    expect(queryResult.dailyCalendarRows.value).toBeUndefined()
    expect(sixMonthCalls()).toEqual([
      ['6M', ['LHV']],
      ['6M', ['SWEDBANK']],
    ])
  })

  it('asks again every five minutes while the day before the latest day has no row', async () => {
    mockSeries([row('2023-12-28'), row('2023-12-29')], [row('2023-12-29'), row('2023-12-30')])
    setupQuery()
    await flushPromises()

    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()
    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()

    expect(sixMonthCalls()).toHaveLength(2)
  })

  it('asks again at five minutes although the current row refreshed at four', async () => {
    mockSeries([row('2023-12-28'), row('2023-12-29')], [row('2023-12-29'), row('2023-12-30')])
    const { queryClient } = setupQuery()
    await flushPromises()
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue({
      ...row('2023-12-31'),
      totalValue: 1,
    })

    vi.advanceTimersByTime(FIVE_MINUTES - ONE_MINUTE)
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'current'] })
    await flushPromises()
    const callsAtFourMinutes = sixMonthCalls().length
    vi.advanceTimersByTime(ONE_MINUTE)
    await flushPromises()

    expect([callsAtFourMinutes, sixMonthCalls().length]).toEqual([1, 2])
  })

  it('asks again after five minutes when the current row arrives after the series', async () => {
    let resolveCurrent: (summary: PortfolioSummaryDto) => void = () => {}
    vi.mocked(portfolioSummaryService.getCurrent).mockReturnValue(
      new Promise(resolve => {
        resolveCurrent = resolve
      })
    )
    mockSeries([row('2023-12-28'), row('2023-12-29')])
    setupQuery()
    await flushPromises()
    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()
    const callsBeforeCurrent = sixMonthCalls().length

    resolveCurrent(row('2023-12-31'))
    await flushPromises()
    vi.advanceTimersByTime(FIVE_MINUTES)
    await flushPromises()

    expect([callsBeforeCurrent, sixMonthCalls().length]).toEqual([1, 2])
  })

  it('does not ask again while the day before the latest day has a row', async () => {
    setupQuery()
    await flushPromises()

    vi.advanceTimersByTime(FIVE_MINUTES * 3)
    await flushPromises()

    expect(sixMonthCalls()).toHaveLength(1)
  })

  it('asks again when the date of the current row changes', async () => {
    const { queryClient } = setupQuery()
    await flushPromises()
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(row('2024-01-01'))

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'current'] })
    await flushPromises()

    expect(sixMonthCalls()).toHaveLength(2)
  })

  it('does not ask again when the current row refreshes on the same date', async () => {
    const { queryClient } = setupQuery()
    await flushPromises()

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'current'] })
    await flushPromises()

    expect(sixMonthCalls()).toHaveLength(1)
  })

  it('asks for the older rows once, and only after the first press', async () => {
    mockHistory([row('2023-06-01')])
    const { queryResult } = setupQuery()
    await flushPromises()
    const before = stateOf(queryResult)

    await press(queryResult)
    await press(queryResult)

    expect([before, maxCalls(), queryResult.calendarHistory.value]).toEqual([
      [0, 'idle'],
      [['MAX', undefined]],
      'loaded',
    ])
  })

  it('puts the older rows before the six-month rows and keeps the six-month row where both answer', async () => {
    mockHistory([
      { ...row('2023-12-30'), totalValue: 1 },
      { ...row('2023-12-29'), totalValue: 1 },
      row('2023-06-02'),
      row('2023-06-01'),
    ])
    const { queryResult } = setupQuery()
    await flushPromises()

    await press(queryResult)

    expect(
      queryResult.dailyCalendarRows.value?.map(({ date, totalValue }) => [date, totalValue])
    ).toEqual([
      ['2023-06-01', 10000],
      ['2023-06-02', 10000],
      ['2023-12-29', 10000],
      ['2023-12-30', 10000],
      ['2023-12-31', 10000],
    ])
  })

  it('preserves a MAX row inside the recent date range when recent history omits it', async () => {
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
      if (range === TimeRange.MAX) return [row('2023-12-30'), row('2023-06-01')]
      if (range === TimeRange.SIX_MONTHS) return [row('2023-12-29')]
      return []
    })
    const { queryResult } = setupQuery()
    await flushPromises()

    await press(queryResult)

    expect(datesOf(queryResult.dailyCalendarRows.value)).toEqual([
      '2023-06-01',
      '2023-12-29',
      '2023-12-30',
      '2023-12-31',
    ])
  })

  it('does not ask for the older rows again when the window regains focus', async () => {
    mockHistory([row('2023-06-01')])
    const { queryResult } = setupQuery()
    await flushPromises()
    await press(queryResult)

    window.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect([sixMonthCalls().length, maxCalls().length]).toEqual([2, 1])
  })

  it('does not ask for the older rows again when the six-month rows are asked again', async () => {
    mockHistory([row('2023-06-01')])
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    await press(queryResult)

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'daily-calendar'] })
    await flushPromises()

    expect([sixMonthCalls().length, maxCalls().length]).toEqual([2, 1])
  })

  it('asks a failed request once more, then waits for the next press', async () => {
    mockHistory(new Error('down'), new Error('down'), [row('2023-06-01')])
    const { queryResult } = setupQuery()
    await flushPromises()
    await press(queryResult)
    const first = stateOf(queryResult)
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    const failed = stateOf(queryResult)

    await press(queryResult)

    expect([first, failed, maxCalls().length, queryResult.calendarHistory.value]).toEqual([
      [1, 'loading'],
      [2, 'idle'],
      3,
      'loaded',
    ])
  })

  it('does not ask on window focus after a failed load', async () => {
    mockHistory(new Error('down'))
    const { queryResult } = setupQuery()
    await flushPromises()
    await press(queryResult)
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    window.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect(stateOf(queryResult)).toEqual([2, 'idle'])
  })

  it('reports the older rows as loaded when the answer holds no earlier day', async () => {
    mockHistory([row('2023-12-29'), row('2023-12-30')])
    const { queryResult } = setupQuery()
    await flushPromises()

    await press(queryResult)

    expect([
      queryResult.calendarHistory.value,
      datesOf(queryResult.dailyCalendarRows.value),
    ]).toEqual(['loaded', ['2023-12-29', '2023-12-30', '2023-12-31']])
  })

  it('asks nothing for a platform selection chosen while the older rows load', async () => {
    const platforms = ref(['LHV'])
    const { queryResult } = setupQuery(platforms)
    await flushPromises()
    vi.mocked(portfolioSummaryService.getSeries).mockReturnValue(new Promise(() => {}))
    await press(queryResult)

    platforms.value = ['SWEDBANK']
    await flushPromises()

    expect([maxCalls(), queryResult.calendarHistory.value]).toEqual([[['MAX', ['LHV']]], 'idle'])
  })

  it('asks for the older rows again when the summary is recalculated', async () => {
    mockHistory([row('2023-06-01')])
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    await press(queryResult)

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()

    expect(maxCalls()).toHaveLength(2)
  })

  it('keeps the older rows when asking again fails, and asks on the next press', async () => {
    mockHistory([row('2023-06-01')], new Error('down'), new Error('down'), [row('2023-06-01')])
    const { queryResult, queryClient } = setupQuery()
    await flushPromises()
    await press(queryResult)
    queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    const kept = [...stateOf(queryResult), datesOf(queryResult.dailyCalendarRows.value)?.[0]]

    await press(queryResult)

    expect([kept, maxCalls().length]).toEqual([[3, 'loaded', '2023-06-01'], 4])
  })
})
