import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
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
})
