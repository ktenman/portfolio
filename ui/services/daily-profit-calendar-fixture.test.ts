import { describe, it, expect } from 'vitest'
import { createDailyProfitCalendar } from '../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { dayOn } from './daily-profit-calendar'
import { mergeHistoricalWithCurrent } from './summary-aggregator'

describe('daily profit calendar of the visual fixture', () => {
  const calendar = createDailyProfitCalendar(
    mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY),
    '2026-01-15'
  )

  it('spans 27 weeks from Mon 30 Jun to Wed 31 Dec 2025 with 30 Dec missing', () => {
    expect([
      calendar.weeks,
      calendar.offset,
      calendar.days[0].name,
      calendar.days[calendar.days.length - 1].date,
      calendar.days.filter(day => day.kind === 'missing').map(day => day.date),
    ]).toEqual([27, 0, 'Mon 30 Jun 2025, starting day', '2025-12-31', ['2025-12-30']])
  })

  it('counts 124 up, 39 down and 20 flat days and averages +€239.83', () => {
    expect([calendar.upDays, calendar.downDays, calendar.flatDays, calendar.averageDay]).toEqual([
      124, 39, 20, 239.83,
    ])
  })

  it('finds the best day on 26 Dec and the worst on 6 Dec', () => {
    expect([calendar.best?.name, calendar.worst?.name]).toEqual([
      'Fri 26 Dec 2025, +€1,012.89, +0.58%',
      'Sat 6 Dec 2025, −€425.21, −0.26%',
    ])
    expect(calendar.best?.detail).toBe(
      'Best of 182 single days · closed at €174,396.35 · XIRR 20.69%'
    )
  })

  it('puts 17 Jul in the palest gain step, 21 Nov among the flat days and no day in a darkest step', () => {
    expect([
      dayOn(calendar, '2025-07-17')?.name,
      dayOn(calendar, '2025-07-17')?.step,
      dayOn(calendar, '2025-11-21')?.name,
      dayOn(calendar, '2025-11-21')?.step,
      calendar.days.filter(day => day.step === 'gain-4' || day.step === 'loss-4'),
    ]).toEqual([
      'Thu 17 Jul 2025, +€54.07, +0.05%',
      'gain-1',
      'Fri 21 Nov 2025, +€56.59, +0.04%',
      'flat',
      [],
    ])
  })

  it('describes 31 Dec, the day after the missing day', () => {
    expect(dayOn(calendar, '2025-12-31')).toMatchObject({
      name: 'Wed 31 Dec 2025, +€1,072.86, +0.61%',
      detail: 'Closed at €178,204.06 · XIRR 21.18%',
      note: 'Covers 2 days, no daily summary for 30 Dec',
    })
  })

  it('describes 30 Jun, the starting day', () => {
    expect(dayOn(calendar, '2025-06-30')).toMatchObject({
      change: null,
      percent: null,
      detail: 'Closed at €103,031.33 · XIRR 10.68%',
      note: 'Starting day, counted in the earlier period',
    })
  })
})

describe('earlier period of the visual fixture', () => {
  const calendar = createDailyProfitCalendar(
    mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY),
    '2026-01-15',
    '2025-06-30'
  )
  const { days } = calendar

  it('lays the earlier period out in two week columns', () => {
    expect([calendar.weeks, calendar.offset, calendar.months]).toEqual([
      2,
      4,
      [{ column: 0, label: 'Jun' }],
    ])
  })

  it('starts the earlier period on 27 Jun', () => {
    expect([days[0].name, days[0].detail, days[0].note]).toEqual([
      'Fri 27 Jun 2025, starting day',
      'Closed at €102,060.28 · XIRR 10.25%',
      'Starting day, not counted or ranked',
    ])
  })

  it('counts three days up and none down or flat', () => {
    expect([
      calendar.upDays,
      calendar.downDays,
      calendar.flatDays,
      calendar.averageDay,
      calendar.worst,
    ]).toEqual([3, 0, 0, 151.79, null])
  })

  it('ranks the three days of the earlier period', () => {
    expect(days.slice(1).map(day => [day.name, day.detail])).toEqual([
      ['Sat 28 Jun 2025, +€251.29, +0.25%', 'Best of 3 days · closed at €102,483.46 · XIRR 10.40%'],
      [
        'Sun 29 Jun 2025, +€137.07, +0.13%',
        '2nd best of 3 days · closed at €102,792.42 · XIRR 10.54%',
      ],
      [
        'Mon 30 Jun 2025, +€67.01, +0.07%',
        '3rd best of 3 days · closed at €103,031.33 · XIRR 10.68%',
      ],
    ])
  })

  it('names 28 Jun the best day', () => {
    expect(calendar.best?.date).toBe('2025-06-28')
  })
})

describe('daily profit calendar of the sample rows', () => {
  const calendar = createDailyProfitCalendar()

  it('ranks 9 Dec best and 3 Dec worst of six single days and leaves 6 Dec unranked', () => {
    expect([calendar.best?.detail, calendar.worst?.detail]).toEqual([
      'Best of 6 single days · closed at €100,000.00 · XIRR 20.69%',
      'Worst of 6 single days · closed at €100,000.00 · XIRR 20.69%',
    ])
    expect([calendar.best?.date, calendar.worst?.date]).toEqual(['2025-12-09', '2025-12-03'])
    expect(dayOn(calendar, '2025-12-06')).toMatchObject({
      detail: 'Closed at €100,000.00 · XIRR 20.69%',
      note: 'Covers 2 days, no daily summary for 5 Dec',
    })
  })

  it('reads 8 Dec as the second worst of six single days', () => {
    expect(dayOn(calendar, '2025-12-08')?.detail).toBe(
      '2nd worst of 6 single days · closed at €100,000.00 · XIRR 20.69%'
    )
  })
})
