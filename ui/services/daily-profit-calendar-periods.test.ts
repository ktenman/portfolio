import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import {
  createCalendarRow as row,
  createDailyProfitCalendar as build,
  createPeriodRows,
} from '../tests/fixtures'
import { addDays, daysBetween } from '../utils/iso-dates'
import { buildDailyProfitCalendar, dayOn, periodLastDays } from './daily-profit-calendar'

const TODAY = '2030-01-01'

const daily = (first: string, last: string): PortfolioSummaryDto[] =>
  Array.from({ length: daysBetween(first, last) + 1 }, (_, index) =>
    row(addDays(first, index), index * 10)
  )

const countedDates = (rows: PortfolioSummaryDto[], lastDay: string) =>
  build(rows, TODAY, lastDay)
    .days.filter(day => day.kind === 'counted')
    .map(day => day.date)

const periodsOf = (rows: PortfolioSummaryDto[]) =>
  periodLastDays(rows).map(lastDay => [
    build(rows, TODAY, lastDay).days[0].date,
    lastDay,
    countedDates(rows, lastDay).length,
  ])

const sixYears = () => daily('2020-01-02', '2026-10-11')

const yearWithout30Jun = () =>
  daily('2025-01-01', '2025-12-31').filter(({ date }) => date !== '2025-06-30')

describe('periods', () => {
  it('counts every row but the oldest in exactly one period', () => {
    const rows = sixYears()
    const counted = periodLastDays(rows).flatMap(lastDay => countedDates(rows, lastDay))

    expect([
      rows.length,
      periodLastDays(rows).length,
      counted.length,
      new Set(counted).size,
    ]).toEqual([2475, 14, 2474, 2474])
  })

  it('ends each earlier period on the starting day of the period after it', () => {
    const periods = periodsOf(sixYears())

    expect([...periods.slice(0, 3), periods[13]]).toEqual([
      ['2026-04-11', '2026-10-11', 183],
      ['2025-10-11', '2026-04-11', 182],
      ['2025-04-11', '2025-10-11', 183],
      ['2020-01-02', '2020-04-11', 100],
    ])
  })

  it('steps back from 31 Aug to the last day of February in a leap year', () => {
    const rows = daily('2023-08-01', '2024-08-31')

    expect(periodsOf(rows)).toEqual([
      ['2024-02-29', '2024-08-31', 184],
      ['2023-08-29', '2024-02-29', 184],
      ['2023-08-01', '2023-08-29', 28],
    ])
  })

  it('steps back from 31 Aug to the last day of February in a plain year', () => {
    const rows = daily('2024-08-01', '2025-08-31')

    expect(periodsOf(rows)).toEqual([
      ['2025-02-28', '2025-08-31', 184],
      ['2024-08-28', '2025-02-28', 184],
      ['2024-08-01', '2024-08-28', 27],
    ])
  })

  it('starts a period on the next row when the day six months before its last day has no row', () => {
    const rows = yearWithout30Jun()

    expect(periodsOf(rows)).toEqual([
      ['2025-07-01', '2025-12-31', 183],
      ['2025-01-01', '2025-07-01', 180],
    ])
  })

  it('shows the day without a row as missing in the earlier period', () => {
    const rows = yearWithout30Jun()
    const calendar = build(rows, TODAY, '2025-07-01')

    expect([dayOn(calendar, '2025-06-30')?.kind, dayOn(calendar, '2025-07-01')?.note]).toEqual([
      'missing',
      'Covers 2 days, no daily summary for 30 Jun',
    ])
  })

  it('builds the oldest period from two rows', () => {
    const rows = [row('2025-06-29', 900), ...daily('2025-06-30', '2025-12-31')]

    expect(periodsOf(rows)).toEqual([
      ['2025-06-30', '2025-12-31', 184],
      ['2025-06-29', '2025-06-30', 1],
    ])
  })

  it('has no period before the oldest row', () => {
    const rows = daily('2025-06-30', '2025-12-31')

    expect([periodLastDays(rows), buildDailyProfitCalendar(rows, TODAY, '2025-06-30')]).toEqual([
      ['2025-12-31'],
      null,
    ])
  })

  it('returns to the same period after a step back and a step forward', () => {
    const rows = createPeriodRows()

    expect(buildDailyProfitCalendar(rows, TODAY, periodLastDays(rows)[0])).toEqual(
      buildDailyProfitCalendar(rows, TODAY)
    )
  })

  it('moves every period by a day when the latest day moves by a day', () => {
    const rows = [...sixYears(), row('2026-10-12', 24750)]

    expect([periodLastDays(rows).length, periodLastDays(rows).slice(0, 3)]).toEqual([
      14,
      ['2026-10-12', '2026-04-12', '2025-10-12'],
    ])
  })

  it('reads closed at on the last day of an earlier period while the latest day is today', () => {
    const rows = createPeriodRows()
    const lastDetail = (lastDay?: string) => {
      const { days } = build(rows, '2025-12-31', lastDay)
      return days[days.length - 1].detail
    }

    expect([lastDetail(), lastDetail('2025-06-30')]).toEqual([
      'Worst of 2 single days · now at €100,000.00 · XIRR 20.69%',
      'Best of 2 single days · closed at €100,000.00 · XIRR 20.69%',
    ])
  })

  it('tells on the starting day whether an earlier period counts it', () => {
    const rows = createPeriodRows()

    expect(periodLastDays(rows).map(lastDay => build(rows, TODAY, lastDay).days[0].note)).toEqual([
      'Starting day, counted in the earlier period',
      'Starting day, counted in the earlier period',
      'Starting day, not counted or ranked',
    ])
  })

  it('ends the periods where more than six months have no row', () => {
    const rows = [
      row('2024-12-30', 100),
      row('2024-12-31', 200),
      row('2025-09-01', 300),
      row('2025-09-02', 400),
      row('2025-12-31', 500),
    ]

    expect([periodLastDays(rows), build(rows).days[0].date, build(rows).days[0].note]).toEqual([
      ['2025-12-31'],
      '2025-09-01',
      'Starting day, not counted or ranked',
    ])
  })

  it('ends the periods at two rows that share a date', () => {
    const rows = [row('2025-12-29', 1), row('2025-12-29', 2), row('2025-12-31', 3)]

    expect(periodLastDays(rows)).toEqual(['2025-12-31'])
  })
})
