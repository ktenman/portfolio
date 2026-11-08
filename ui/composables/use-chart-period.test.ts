import { afterEach, describe, expect, it } from 'vitest'
import { effectScope, isReactive, nextTick, ref, type EffectScope } from 'vue'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'
import type { ChartSummary } from './use-portfolio-chart'
import { useChartPeriod } from './use-chart-period'

const day = (date: string, totalValue: number, totalProfit: number): PortfolioSummaryDto => ({
  date,
  totalValue,
  totalProfit,
  xirrAnnualReturn: 0,
  realizedProfit: 0,
  unrealizedProfit: 0,
  earningsPerDay: 0,
  earningsPerMonth: 0,
  totalProfitChange24h: null,
})

const SERIES = [
  day('2026-01-01', 1000, 0),
  day('2026-01-06', 1050, 50),
  day('2026-01-11', 1250, 100),
  day('2026-01-16', 1260, 110),
  day('2026-01-21', 1300, 150),
]

const MID_JANUARY = { from: '2026-01-06', to: '2026-01-16' }

const scopes: EffectScope[] = []

afterEach(() => scopes.splice(0).forEach(scope => scope.stop()))

const usePage = (series: PortfolioSummaryDto[] = SERIES) => {
  const range = ref<TimeRange>(TimeRange.ONE_YEAR)
  const chart = ref<ChartSummary[]>(series)
  const performance = ref<PortfolioSummaryDto[]>(series)
  const scope = effectScope()
  scopes.push(scope)
  const page = scope.run(() => useChartPeriod(range, chart, performance))!
  return { range, chart, performance, ...page }
}

const datesOf = (summaries: { date: string }[]) => summaries.map(summary => summary.date)

const zoomedTo = (period: { from: string; to: string }) => {
  const page = usePage()
  page.period.value = period
  page.zoomIn()
  return page
}

const loadSeries = async (page: ReturnType<typeof usePage>, series: PortfolioSummaryDto[]) => {
  page.chart.value = series
  page.performance.value = series
  await nextTick()
}

describe('useChartPeriod', () => {
  it('should show the whole loaded series before a zoom', () => {
    expect(datesOf(usePage().chartSummaries.value)).toEqual(datesOf(SERIES))
  })

  it('should hand on plain summaries that a drag reads without tracking', () => {
    expect(isReactive(usePage().performanceSummaries.value[0])).toBe(false)
  })

  it('should take today from the latest loaded day', () => {
    expect(usePage().today.value).toBe('2026-01-21')
  })

  it('should take today from the latest day of a series served newest first', () => {
    expect(usePage([...SERIES].reverse()).today.value).toBe('2026-01-21')
  })

  it('should measure a period across a series served newest first', () => {
    const page = usePage([...SERIES].reverse())
    page.period.value = MID_JANUARY

    expect(page.change.value?.amount).toBe(60)
  })

  it('should leave the change to the preset while nothing is drawn or zoomed', () => {
    expect(usePage().change.value).toBeNull()
  })

  it('should measure the change across a drawn period', () => {
    const page = usePage()
    page.period.value = MID_JANUARY

    expect(page.change.value).toEqual({
      amount: 60,
      percent: 5,
      phrase: 'from 6 January to 16 January',
    })
  })

  it('should zoom the chart to the drawn period', () => {
    const page = zoomedTo(MID_JANUARY)

    expect(datesOf(page.chartSummaries.value)).toEqual(['2026-01-06', '2026-01-11', '2026-01-16'])
  })

  it('should zoom the performance series with the chart', () => {
    const page = zoomedTo(MID_JANUARY)

    expect(datesOf(page.performanceSummaries.value)).toEqual([
      '2026-01-06',
      '2026-01-11',
      '2026-01-16',
    ])
  })

  it('should let go of the drawn period once zoomed', () => {
    expect(zoomedTo(MID_JANUARY).period.value).toBeNull()
  })

  it('should keep measuring the zoomed window', () => {
    expect(zoomedTo(MID_JANUARY).change.value).toEqual({
      amount: 60,
      percent: 5,
      phrase: 'from 6 January to 16 January',
    })
  })

  it('should name the zoom for the More cell', () => {
    expect(zoomedTo(MID_JANUARY).zoomLabel.value).toBe('6 Jan – 16 Jan')
  })

  it('should offer the way back to the preset while zoomed', () => {
    expect(zoomedTo(MID_JANUARY).backLabel.value).toBe('1Y')
  })

  it('should offer no way back before a zoom', () => {
    expect(usePage().backLabel.value).toBeNull()
  })

  it('should drop a period that covers the whole view instead of zooming', () => {
    const page = zoomedTo({ from: '2026-01-01', to: '2026-01-21' })

    expect([page.period.value, page.zoomLabel.value]).toEqual([null, null])
  })

  it('should go back to the whole preset', () => {
    const page = zoomedTo(MID_JANUARY)
    page.back()

    expect(datesOf(page.chartSummaries.value)).toEqual(datesOf(SERIES))
  })

  it('should pick a preset', () => {
    const page = usePage()
    page.pick(TimeRange.ONE_MONTH)

    expect(page.range.value).toBe(TimeRange.ONE_MONTH)
  })

  it('should let go of the zoom when a preset is picked', () => {
    const page = zoomedTo(MID_JANUARY)
    page.pick(TimeRange.ONE_MONTH)

    expect(page.zoomLabel.value).toBeNull()
  })

  it('should let go of a drawn period when a preset is picked', () => {
    const page = usePage()
    page.period.value = MID_JANUARY
    page.pick(TimeRange.ONE_YEAR)

    expect(page.period.value).toBeNull()
  })

  it('should clamp the period to a series that starts later', async () => {
    const page = usePage()
    page.period.value = { from: '2026-01-01', to: '2026-01-16' }
    await loadSeries(page, SERIES.slice(1))

    expect(page.period.value).toEqual(MID_JANUARY)
  })

  it('should drop a period that a new series leaves shorter than two days', async () => {
    const page = usePage()
    page.period.value = { from: '2026-01-01', to: '2026-01-06' }
    await loadSeries(page, SERIES.slice(1))

    expect(page.period.value).toBeNull()
  })

  it('should keep a drawn period that ends today while today is briefly missing', async () => {
    const page = usePage()
    page.period.value = { from: '2026-01-06', to: '2026-01-21' }
    await loadSeries(page, SERIES.slice(0, -1))
    await loadSeries(page, SERIES)

    expect(page.period.value).toEqual({ from: '2026-01-06', to: '2026-01-21' })
  })

  it('should keep a zoom that ends today while today is briefly missing', async () => {
    const page = zoomedTo({ from: '2026-01-06', to: '2026-01-21' })
    await loadSeries(page, SERIES.slice(0, -1))
    await loadSeries(page, SERIES)

    expect(datesOf(page.chartSummaries.value)).toEqual(datesOf(SERIES.slice(1)))
  })

  it('should not bring back a period that new data dropped', async () => {
    const page = usePage()
    page.period.value = { from: '2026-01-01', to: '2026-01-06' }
    await loadSeries(page, SERIES.slice(1))
    await loadSeries(page, SERIES)

    expect(page.period.value).toBeNull()
  })

  it('should not bring back a zoom that new data dropped', async () => {
    const page = zoomedTo({ from: '2026-01-01', to: '2026-01-06' })
    await loadSeries(page, SERIES.slice(1))
    await loadSeries(page, SERIES)

    expect(page.zoomLabel.value).toBeNull()
  })

  it('should drop a zoom that a new series leaves shorter than two days', async () => {
    const page = zoomedTo({ from: '2026-01-01', to: '2026-01-06' })
    await loadSeries(page, SERIES.slice(1))

    expect(page.zoomLabel.value).toBeNull()
  })
})
