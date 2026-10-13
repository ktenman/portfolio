import { describe, expect, it } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow as row } from '../tests/fixtures'
import {
  buildMonthlyProfit,
  formatMonth,
  formatMonthlyPercent,
  monthOn,
  monthlyStep,
  moveMonth,
} from './monthly-profit'

const build = (rows: PortfolioSummaryDto[]) => {
  const calendar = buildMonthlyProfit(rows, '2030-01-01')
  expect(calendar).not.toBeNull()
  if (!calendar) throw new Error('Expected observed monthly profit')
  return calendar
}

describe('monthly color steps and formatting', () => {
  it.each([
    [0, 'flat'],
    [0.249, 'flat'],
    [0.25, 'gain-1'],
    [0.3, 'gain-1'],
    [0.94, 'gain-1'],
    [0.96, 'gain-2'],
    [1, 'gain-2'],
    [2.44, 'gain-2'],
    [2.46, 'gain-3'],
    [2.5, 'gain-3'],
    [4.94, 'gain-3'],
    [4.96, 'gain-4'],
    [5, 'gain-4'],
    [-0.249, 'flat'],
    [-0.25, 'loss-1'],
    [-1, 'loss-2'],
    [-2.5, 'loss-3'],
    [-5, 'loss-4'],
  ] as const)('colors a percentage of %s after one-decimal rounding', (percent, step) => {
    expect(monthlyStep(-100, percent)).toBe(step)
  })

  it.each([
    [100, null, 'gain-1'],
    [-100, null, 'loss-1'],
    [0, null, 'flat'],
    [-0, null, 'flat'],
    [100, Number.NaN, 'gain-1'],
    [-100, Number.POSITIVE_INFINITY, 'loss-1'],
  ] as const)(
    'falls back to the euro side %s for unavailable percentage %s',
    (change, percent, step) => {
      expect(monthlyStep(change, percent)).toBe(step)
    }
  )

  it.each([
    [0, '0.0%', '0.0'],
    [-0, '0.0%', '0.0'],
    [-0.04, '0.0%', '0.0'],
    [0.051, '+0.1%', '+0.1'],
    [5.42, '+5.4%', '+5.4'],
    [-3.04, '−3.0%', '−3.0'],
    [100, '+100.0%', '+100'],
    [-100.49, '−100.5%', '−100'],
    [12345.678, '+12345.7%', '+12346'],
    [null, '—', '—'],
    [Number.NaN, '—', '—'],
    [Number.POSITIVE_INFINITY, '—', '—'],
  ] as const)('formats %s for full descriptions and compact cells', (percent, full, grid) => {
    expect(formatMonthlyPercent(percent)).toBe(full)
    expect(formatMonthlyPercent(percent, true)).toBe(grid)
  })

  it('formats a stable month key without local date conversion', () => {
    expect(formatMonth('2025-06')).toBe('Jun 2025')
    expect(formatMonth('2025-06', true)).toBe('June 2025')
    expect(formatMonth('2025-12', true)).toBe('December 2025')
  })
})

describe('monthly statistics and ranks', () => {
  it('counts single months by percentage color rather than the euro sign', () => {
    const calendar = build([
      row('2025-01-31', 0, 1000),
      row('2025-02-01', -40, 960),
      row('2025-02-28', 60, 10100),
      row('2025-03-31', 160, 1160),
      row('2025-04-30', 160, 1160),
    ])

    expect([calendar.upMonths, calendar.downMonths, calendar.flatMonths]).toEqual([1, 1, 1])
    expect(calendar.best?.key).toBe('2025-03')
    expect(calendar.worst).toBeNull()
  })

  it('excludes cross-month intervals from counts, average, ranks and shortcuts', () => {
    const calendar = build([
      row('2025-01-31', 0, 1000),
      row('2025-02-28', 100, 1100),
      row('2025-03-28', 200, 1200),
      row('2025-04-30', 1200, 2200),
      row('2025-05-31', 300, 1300),
    ])

    expect([
      calendar.upMonths,
      calendar.downMonths,
      calendar.flatMonths,
      calendar.excludedMonths,
    ]).toEqual([2, 1, 0, 1])
    expect(calendar.averageMonth).toBe(-233.33)
    expect(calendar.best?.key).toBe('2025-03')
    expect(calendar.worst?.key).toBe('2025-05')
    expect(calendar.months[2].rank).toBe('')
    expect(calendar.months[0].rank).toBe('Best of 3 single months')
  })

  it('averages raw endpoint differences and rounds once', () => {
    const calendar = build([
      row('2025-01-31', 0),
      row('2025-02-28', 0.004),
      row('2025-03-31', 0.008),
      row('2025-04-30', 0.017),
    ])

    expect(calendar.months.map(month => month.change)).toEqual([0, 0, 0.01])
    expect(calendar.averageMonth).toBe(0.01)
  })

  it('shows no average or shortcuts when every interval spans multiple months', () => {
    const calendar = build([row('2025-01-28', 0), row('2025-03-31', 100)])

    expect([
      calendar.averageMonth,
      calendar.best,
      calendar.worst,
      calendar.upMonths,
      calendar.downMonths,
      calendar.flatMonths,
      calendar.excludedMonths,
    ]).toEqual([null, null, null, 0, 0, 0, 1])
  })

  it('shares tied euro ranks and chooses the later month for shortcuts', () => {
    const calendar = build([
      row('2024-12-31', 0, 1000),
      row('2025-01-31', 200, 1200),
      row('2025-02-28', 100, 1100),
      row('2025-03-31', 300, 1300),
      row('2025-04-30', 200, 1200),
      row('2025-05-31', 250, 1250),
      row('2025-06-30', 250, 1250),
    ])

    expect(calendar.months.map(month => month.rank)).toEqual([
      'Best of 6 months',
      'Worst of 6 months',
      'Best of 6 months',
      'Worst of 6 months',
      '3rd best of 6 months',
      '',
    ])
    expect(calendar.best?.key).toBe('2025-03')
    expect(calendar.worst?.key).toBe('2025-04')
    expect(calendar.months[0].name).toContain('ranked by euro profit')
  })

  it('uses singular rank wording with one eligible month', () => {
    const month = build([row('2025-01-31', 0), row('2025-02-28', 100)]).months[0]

    expect(month.rank).toBe('Best of 1 month')
  })

  it('omits a positive shortcut for a loss-only history', () => {
    const calendar = build([row('2025-01-31', 100), row('2025-02-28', 0)])

    expect(calendar.best).toBeNull()
    expect(calendar.worst?.key).toBe('2025-02')
  })

  it.each([
    [['2025-01-31', '2025-02-28'], 'Feb 2025'],
    [['2025-05-31', '2025-06-30', '2025-12-31'], 'Jun – Dec 2025'],
    [['2019-12-31', '2020-01-31', '2026-10-12'], 'Jan 2020 – Oct 2026'],
  ])('uses calculated months to label the full span', (dates, span) => {
    const calendar = build(dates.map((date, index) => row(date, index * 100)))

    expect(calendar.span).toBe(span)
  })
})

describe('monthly navigation', () => {
  const rows = [
    row('2022-12-31', 0),
    row('2023-01-31', 100),
    row('2023-03-31', 200),
    row('2024-01-31', 300),
    row('2026-01-31', 400),
  ]

  it('looks up only observed month keys', () => {
    const calendar = build(rows)

    expect(monthOn(calendar, '2023-01')).toBe(calendar.months[0])
    expect(monthOn(calendar, '2023-02')).toBeUndefined()
    expect(monthOn(calendar, '2022-12')).toBeUndefined()
  })

  it('moves chronologically through missing slots and year boundaries', () => {
    const calendar = build(rows)

    expect(moveMonth(calendar, '2023-01', 1)).toBe('2023-03')
    expect(moveMonth(calendar, '2023-03', 1)).toBe('2024-01')
    expect(moveMonth(calendar, '2026-01', -1)).toBe('2024-01')
    expect(moveMonth(calendar, '2023-01', -1)).toBeNull()
    expect(moveMonth(calendar, '2026-01', 1)).toBeNull()
  })

  it('continues annual movement in twelve-month increments through missing years', () => {
    const calendar = build(rows)

    expect(moveMonth(calendar, '2023-01', 12)).toBe('2024-01')
    expect(moveMonth(calendar, '2024-01', 12)).toBe('2026-01')
    expect(moveMonth(calendar, '2026-01', -12)).toBe('2024-01')
    expect(moveMonth(calendar, '2023-03', 12)).toBeNull()
  })

  it('does not navigate from a month that is absent from the observed history', () => {
    const calendar = build(rows)

    expect(moveMonth(calendar, '2023-02', 1)).toBeNull()
  })
})
