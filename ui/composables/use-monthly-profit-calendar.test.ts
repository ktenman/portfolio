import { describe, it, expect } from 'vitest'
import { nextTick, reactive, watch } from 'vue'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import type { MonthlyProfit, ProfitMonth } from '../services/monthly-profit'
import { useMonthlyProfitCalendar } from './use-monthly-profit-calendar'

const november = (): ProfitMonth => ({
  key: '2025-11',
  opening: createPortfolioSummaryDto({ date: '2025-10-31' }),
  closing: createPortfolioSummaryDto({ date: '2025-11-30', totalProfit: 1300 }),
  rawChange: 300,
  change: 300,
  percent: 3,
  factor: 1.03,
  step: 'gain-3',
  eligible: true,
  coverage: [],
  name: 'November 2025, +€300.00, +3.0%, Closed at €10,000.00, XIRR 10.00%',
  rank: 'Best of 2 months',
  closingLabel: 'Closed at €10,000.00 · as of 30 Nov 2025',
  isCurrent: false,
})

const december = (): ProfitMonth => ({
  ...november(),
  key: '2025-12',
  opening: createPortfolioSummaryDto({ date: '2025-11-30', totalProfit: 1300 }),
  closing: createPortfolioSummaryDto({ date: '2025-12-28', totalProfit: 1500 }),
  rawChange: 200,
  change: 200,
  percent: 2,
  factor: 1.02,
  step: 'gain-2',
  coverage: ['to 28 Dec', 'No daily summary after 28 Dec'],
  name: 'December 2025, +€200.00, +2.0%, to 28 Dec, No daily summary after 28 Dec',
  rank: '2nd best of 2 months',
  closingLabel: 'Closed at €10,000.00 · as of 28 Dec 2025',
})

const calendarWith = (months = [november(), december()]): MonthlyProfit => ({
  months,
  years: [],
  span: 'Nov – Dec 2025',
  upMonths: 2,
  downMonths: 0,
  flatMonths: 0,
  averageMonth: 250,
  best: months[0] ?? null,
  worst: null,
  excludedMonths: 0,
})

const createState = (calendar: MonthlyProfit | null = calendarWith()) => {
  const props = reactive({ calendar })
  return { props, ...useMonthlyProfitCalendar(props) }
}

describe('useMonthlyProfitCalendar', () => {
  it('should select the latest observed month silently', () => {
    const state = createState()

    expect([state.month.value?.key, state.selectedKey.value, state.announcement.value]).toEqual([
      '2025-12',
      '2025-12',
      '',
    ])
  })

  it('should select the latest month when history becomes available', async () => {
    const state = createState(null)

    state.props.calendar = calendarWith()
    await nextTick()

    expect([state.month.value?.key, state.announcement.value]).toEqual(['2025-12', ''])
  })

  it('should commit a month and announce its complete description once', () => {
    const state = createState()
    const announcements: string[] = []
    watch(state.announcement, value => announcements.push(value), { flush: 'sync' })

    state.select('2025-11')
    state.select('2025-11')

    expect([state.month.value?.key, announcements]).toEqual([
      '2025-11',
      ['November 2025, +€300.00, +3.0%, Closed at €10,000.00, XIRR 10.00%'],
    ])
  })

  it('should preview without changing selection or announcing it', () => {
    const state = createState()

    state.previewKey.value = '2025-11'

    expect([state.preview.value?.key, state.month.value?.key, state.announcement.value]).toEqual([
      '2025-11',
      '2025-12',
      '',
    ])
  })

  it('should restore the committed readout when the preview ends', () => {
    const state = createState()
    state.previewKey.value = '2025-11'

    state.previewKey.value = null

    expect([state.preview.value, state.month.value?.key]).toEqual([undefined, '2025-12'])
  })

  it('should clear a preview when committing the already selected month', () => {
    const state = createState()
    state.previewKey.value = '2025-11'

    state.select('2025-12')

    expect([state.preview.value, state.month.value?.key, state.announcement.value]).toEqual([
      undefined,
      '2025-12',
      '',
    ])
  })

  it('should keep the selected key when a refreshed history adds a month', async () => {
    const state = createState()
    state.select('2025-11')
    state.previewKey.value = '2025-12'

    state.props.calendar = calendarWith([
      { ...november(), change: 400 },
      december(),
      { ...december(), key: '2026-01' },
    ])
    await nextTick()

    expect([
      state.month.value?.key,
      state.month.value?.change,
      state.preview.value,
      state.announcement.value,
    ]).toEqual(['2025-11', 400, undefined, ''])
  })

  it('should silently fall back to the latest when the selected month disappears', async () => {
    const state = createState()
    state.select('2025-11')

    state.props.calendar = calendarWith([december()])
    await nextTick()

    expect([state.month.value?.key, state.announcement.value]).toEqual(['2025-12', ''])
  })

  it('should ignore a missing month when committing selection', () => {
    const state = createState()

    state.select('2025-10')

    expect([state.month.value?.key, state.announcement.value]).toEqual(['2025-12', ''])
  })

  it('should clear the selection when no monthly history remains', async () => {
    const state = createState()
    state.select('2025-11')

    state.props.calendar = null
    await nextTick()

    expect([state.month.value, state.selectedKey.value, state.announcement.value]).toEqual([
      undefined,
      '',
      '',
    ])
  })
})
