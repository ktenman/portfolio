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
  useAuthState: () => ({ isAuthenticated: ref(true) }),
}))

enableAutoUnmount(afterEach)

type Interval = 'daily' | 'monthly'
type HistoryAnswer = PortfolioSummaryDto[] | Error | Promise<PortfolioSummaryDto[]>

const row = (date: string, totalValue = 10000) => createPortfolioSummaryDto({ date, totalValue })
const datesOf = (rows: PortfolioSummaryDto[] | undefined) => rows?.map(summary => summary.date)
const maxCalls = () =>
  vi
    .mocked(portfolioSummaryService.getSeries)
    .mock.calls.filter(([range]) => range === TimeRange.MAX)

const pendingHistory = () => {
  let resolve!: (rows: PortfolioSummaryDto[]) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<PortfolioSummaryDto[]>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const mockHistory = (...answers: HistoryAnswer[]) => {
  let call = 0
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
    if (range === TimeRange.SIX_MONTHS) return [row('2023-12-29'), row('2023-12-30')]
    if (range !== TimeRange.MAX) return []
    const answer = answers[Math.min(call, answers.length - 1)]
    call += 1
    if (answer instanceof Error) throw answer
    return answer
  })
}

const setupQuery = (platforms = ref<string[]>([]), interval: Ref<Interval> = ref('monthly')) => {
  let queryResult!: ReturnType<typeof usePortfolioSummaryQuery>
  const wrapper = renderWithProviders({
    setup() {
      queryResult = usePortfolioSummaryQuery(platforms, ref(TimeRange.ONE_MONTH), interval)
      return {}
    },
    template: '<div></div>',
  })
  return { query: queryResult, queryClient: wrapper.queryClient, platforms, interval }
}

describe('usePortfolioSummaryQuery monthly history', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
      content: [row('2023-12-29'), row('2023-12-30')],
      totalElements: 90,
      totalPages: 3,
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
    mockHistory([row('2023-06-01'), row('2023-12-28')])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads MAX automatically when the restored interval is monthly', async () => {
    const { query } = setupQuery()
    await flushPromises()

    expect(maxCalls()).toEqual([[TimeRange.MAX, undefined]])
    expect(datesOf(query.monthlyHistoryRows.value)).toEqual([
      '2023-06-01',
      '2023-12-28',
      '2023-12-29',
      '2023-12-30',
      '2023-12-31',
    ])
    expect([query.calendarHistoryStatus.value, query.calendarHistoryError.value]).toEqual([
      'success',
      null,
    ])
  })

  it('keeps monthly rows unavailable until MAX succeeds even when recent and current are ready', async () => {
    const history = pendingHistory()
    mockHistory(history.promise)
    const { query } = setupQuery()
    await flushPromises()

    expect(maxCalls()).toHaveLength(1)
    expect(query.monthlyHistoryRows.value).toBeUndefined()
    expect([query.calendarHistoryStatus.value, query.isCalendarHistoryFetching.value]).toEqual([
      'loading',
      true,
    ])
    expect(query.dailyCalendarRows.value).toHaveLength(3)

    history.resolve([row('2023-06-01')])
    await flushPromises()

    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-06-01')
    expect(query.isCalendarHistoryFetching.value).toBe(false)
  })

  it('reports idle in Daily without starting a full-history request', async () => {
    const { query } = setupQuery(undefined, ref('daily'))
    await flushPromises()

    expect(query.calendarHistoryStatus?.value).toBe('idle')
    expect(query.calendarHistoryError.value).toBeNull()
    expect(query.monthlyHistoryRows.value).toBeUndefined()
    expect(maxCalls()).toHaveLength(0)
  })

  it('treats successful empty MAX as complete history', async () => {
    mockHistory([])
    const { query } = setupQuery()
    await flushPromises()

    expect(query.calendarHistoryStatus?.value).toBe('success')
    expect(datesOf(query.monthlyHistoryRows.value)).toEqual([
      '2023-12-29',
      '2023-12-30',
      '2023-12-31',
    ])
  })

  it('reuses successful cached MAX on reentering Monthly', async () => {
    const { query, interval } = setupQuery()
    await flushPromises()
    interval.value = 'daily'
    await flushPromises()
    interval.value = 'monthly'
    await flushPromises()

    expect(maxCalls()).toHaveLength(1)
    expect(query.calendarHistory.value).toBe('loaded')
  })

  it('reports exhausted initial failure and waits for an explicit new activation', async () => {
    mockHistory(new Error('MAX unavailable'), new Error('MAX unavailable'), [row('2023-06-01')])
    const { query, interval } = setupQuery()
    await flushPromises()

    expect(maxCalls()).toHaveLength(1)
    expect(query.calendarHistoryError.value).toBeNull()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect([query.calendarHistoryStatus.value, query.calendarHistoryError.value]).toEqual([
      'error',
      'MAX unavailable',
    ])
    expect(query.monthlyHistoryRows.value).toBeUndefined()
    window.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()
    expect(maxCalls()).toHaveLength(2)

    interval.value = 'daily'
    await flushPromises()
    interval.value = 'monthly'
    await flushPromises()

    expect(maxCalls()).toHaveLength(3)
    expect(query.calendarHistoryStatus.value).toBe('success')
  })

  it('retains successful monthly history after a MAX refresh failure and explicitly retries', async () => {
    mockHistory([row('2023-06-01')], new Error('refresh failed'), new Error('refresh failed'), [
      row('2023-05-31'),
    ])
    const { query, queryClient } = setupQuery()
    await flushPromises()
    const before = datesOf(query.monthlyHistoryRows.value)

    void queryClient.invalidateQueries({
      queryKey: ['portfolio-summary', 'daily-calendar-history'],
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(datesOf(query.monthlyHistoryRows.value)).toEqual(before)
    expect(query.calendarHistory.value).toBe('loaded')
    expect(query.calendarHistoryStatus.value).toBe('error')
    expect(query.monthlyHistoryError.value).toBe('refresh failed')
    window.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(maxCalls()).toHaveLength(3)

    await query.retryCalendarHistory()
    await flushPromises()

    expect(maxCalls()).toHaveLength(4)
    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-05-31')
    expect(query.monthlyHistoryError.value).toBeNull()
  })

  it('keeps successful recent and current overlays when their refresh fails', async () => {
    const { query, queryClient } = setupQuery()
    await flushPromises()
    const before = datesOf(query.monthlyHistoryRows.value)
    vi.mocked(portfolioSummaryService.getCurrent).mockRejectedValue(new Error('current failed'))
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
      if (range === TimeRange.SIX_MONTHS) throw new Error('recent failed')
      return [row('2023-06-01')]
    })

    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'daily-calendar'] })
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'current'] })
    await flushPromises()

    expect(datesOf(query.monthlyHistoryRows.value)).toEqual(before)
    expect(query.monthlyHistoryError.value).toBe('recent failed')
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range =>
      range === TimeRange.SIX_MONTHS ? [row('2023-12-29'), row('2023-12-30')] : [row('2023-06-01')]
    )
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(row('2023-12-31', 12000))

    await query.retryCalendarHistory()
    await flushPromises()

    expect(query.monthlyHistoryError.value).toBeNull()
    const refreshedRows = query.monthlyHistoryRows.value
    expect(refreshedRows?.[refreshedRows.length - 1]?.totalValue).toBe(12000)
    expect(maxCalls()).toHaveLength(2)
  })

  it.each(['interval', 'platform'] as const)(
    'reuses retained MAX after a failed refresh on %s reentry',
    async returnVia => {
      mockHistory([row('2023-06-01')], new Error('refresh failed'))
      vi.mocked(portfolioSummaryService.recalculate).mockResolvedValue({ message: 'Done' })
      const { query, queryClient, interval, platforms } = setupQuery(ref(['LHV']))
      await flushPromises()
      const before = datesOf(query.monthlyHistoryRows.value)
      void queryClient.invalidateQueries({
        queryKey: ['portfolio-summary', 'daily-calendar-history'],
      })
      await flushPromises()
      await vi.advanceTimersByTimeAsync(1000)
      await flushPromises()
      expect(maxCalls()).toHaveLength(3)
      mockHistory([row('2023-04-01')])

      if (returnVia === 'interval') {
        interval.value = 'daily'
        await flushPromises()
        interval.value = 'monthly'
      } else {
        platforms.value = ['SWEDBANK']
        await flushPromises()
        platforms.value = ['LHV']
      }
      await flushPromises()

      const expectedCalls = returnVia === 'interval' ? 3 : 4
      expect(maxCalls()).toHaveLength(expectedCalls)
      expect(datesOf(query.monthlyHistoryRows.value)).toEqual(before)
      expect(query.calendarHistoryStatus.value).toBe('error')
      expect(query.monthlyHistoryError.value).toBe('refresh failed')
      mockHistory([row('2023-05-01')])

      if (returnVia === 'interval') await query.retryCalendarHistory()
      else query.recalculate()
      await flushPromises()

      expect(maxCalls()).toHaveLength(expectedCalls + 1)
      expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-05-01')
      expect(query.monthlyHistoryError.value).toBeNull()
    }
  )

  it('merges full, recent and current data without discarding MAX rows in the recent span', async () => {
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
      if (range === TimeRange.MAX)
        return [row('2023-12-30', 1), row('2023-06-01', 1), row('2023-12-29', 1)]
      if (range === TimeRange.SIX_MONTHS) return [row('2023-12-31', 2), row('2023-12-29', 2)]
      return []
    })
    const { query } = setupQuery()
    await flushPromises()

    expect(
      query.monthlyHistoryRows?.value?.map(({ date, totalValue }) => [date, totalValue])
    ).toEqual([
      ['2023-06-01', 1],
      ['2023-12-29', 2],
      ['2023-12-30', 1],
      ['2023-12-31', 10000],
    ])
  })

  it('switches Monthly platforms to placeholders until the new platform MAX succeeds', async () => {
    const secondHistory = pendingHistory()
    mockHistory([row('2023-06-01')], secondHistory.promise)
    const { query, platforms } = setupQuery(ref(['LHV']))
    await flushPromises()

    platforms.value = ['SWEDBANK']
    await flushPromises()

    expect(maxCalls()).toEqual([
      [TimeRange.MAX, ['LHV']],
      [TimeRange.MAX, ['SWEDBANK']],
    ])
    expect(query.monthlyHistoryRows.value).toBeUndefined()
    secondHistory.resolve([row('2023-07-01')])
    await flushPromises()

    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-07-01')
    platforms.value = ['LHV']
    await flushPromises()

    expect(maxCalls()).toHaveLength(2)
    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-06-01')
  })

  it('hides old-platform rows synchronously when the platform selection changes', async () => {
    const secondHistory = pendingHistory()
    mockHistory([row('2023-06-01')], secondHistory.promise)
    const { query, platforms } = setupQuery(ref(['LHV']))
    await flushPromises()

    platforms.value = ['SWEDBANK']

    expect(query.monthlyHistoryRows.value).toBeUndefined()
    expect(query.dailyCalendarRows.value).toBeUndefined()
    expect(query.calendarHistory.value).toBe('idle')
  })

  it('clears old-platform errors before the new platform request starts', async () => {
    mockHistory(new Error('LHV unavailable'))
    const { query, platforms } = setupQuery(ref(['LHV']))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    mockHistory([row('2023-07-01')])

    platforms.value = ['SWEDBANK']

    expect(query.calendarHistoryError.value).toBeNull()
    expect(query.monthlyHistoryError.value).toBeNull()
    expect(query.calendarHistoryStatus.value).toBe('idle')
    await flushPromises()
    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-07-01')
  })

  it.each(['success', 'failure'] as const)('ignores a late old-platform MAX %s', async outcome => {
    const firstHistory = pendingHistory()
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async (range, platforms) => {
      if (range === TimeRange.SIX_MONTHS) return [row('2023-12-29'), row('2023-12-30')]
      if (range !== TimeRange.MAX) return []
      return platforms?.[0] === 'LHV' ? firstHistory.promise : [row('2023-07-01')]
    })
    const { query, platforms } = setupQuery(ref(['LHV']))
    await flushPromises()
    platforms.value = ['SWEDBANK']
    await flushPromises()

    if (outcome === 'success') firstHistory.resolve([row('2023-06-01')])
    else firstHistory.reject(new Error('old platform failed'))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-07-01')
    expect([query.calendarHistoryStatus.value, query.monthlyHistoryError.value]).toEqual([
      'success',
      null,
    ])
    expect(maxCalls().filter(([, platforms]) => platforms?.[0] === 'SWEDBANK')).toHaveLength(1)
  })

  it('keeps MAX active so recalculation refreshes monthly history', async () => {
    mockHistory([row('2023-06-01')], [row('2023-05-31')])
    vi.mocked(portfolioSummaryService.recalculate).mockResolvedValue({ message: 'Done' })
    const { query } = setupQuery()
    await flushPromises()

    query.recalculate()
    await flushPromises()

    expect(maxCalls()).toHaveLength(2)
    expect(datesOf(query.monthlyHistoryRows.value)?.[0]).toBe('2023-05-31')
  })

  it('suppresses daily pagination immediately when Monthly is requested', async () => {
    const history = pendingHistory()
    mockHistory(history.promise)
    const { query, interval } = setupQuery(undefined, ref('daily'))
    await flushPromises()
    interval.value = 'monthly'

    await query.fetchSummaries()
    await flushPromises()

    expect(
      vi.mocked(portfolioSummaryService.getHistorical).mock.calls.map(([page]) => page)
    ).toEqual([0])
    interval.value = 'daily'
    await query.fetchSummaries()
    await flushPromises()

    expect(
      vi.mocked(portfolioSummaryService.getHistorical).mock.calls.map(([page]) => page)
    ).toEqual([0, 1])
  })
})
