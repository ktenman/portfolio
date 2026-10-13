import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays, daysBetween } from '../utils/iso-dates'
import { buildMonthlyProfit, extremeMonths, monthOn, monthsOf, moveMonth } from './monthly-profit'
import { cellTextOf, stepOfMonth } from './monthly-profit-texts'

const LATER = '2030-01-15'

const row = (date: string, totalProfit = 0, totalValue = 1000): PortfolioSummaryDto => ({
  date,
  totalValue,
  xirrAnnualReturn: 0.1,
  realizedProfit: 0,
  unrealizedProfit: totalProfit,
  totalProfit,
  earningsPerDay: 1,
  earningsPerMonth: 30,
  totalProfitChange24h: null,
})

const everyDay = (first: string, last: string): PortfolioSummaryDto[] =>
  Array.from({ length: daysBetween(first, last) + 1 }, (_, index) => row(addDays(first, index)))

describe('month boundaries', () => {
  it('starts the next month from a baseline on month-end', () => {
    const months = monthsOf(
      [row('2025-01-31'), row('2025-02-01', 10), row('2025-02-28', 20)],
      LATER
    )
    expect(months.map(month => [month.key, month.coverage, month.isSingleMonth])).toEqual([
      ['2025-02', '', true],
    ])
  })

  it('labels the first month from its baseline', () => {
    const months = monthsOf(everyDay('2025-06-27', '2025-06-30'), LATER)
    expect(months.map(month => [month.key, month.coverage])).toEqual([['2025-06', 'from 27 Jun']])
  })

  it('gives the same months for rows in any order', () => {
    const rows = [...everyDay('2025-06-27', '2025-07-05'), row('2025-08-02', 40, 1040)]
    expect(monthsOf([...rows].reverse(), LATER)).toEqual(monthsOf(rows, LATER))
  })

  it('closes a leap February on the 29th without a coverage label', () => {
    const months = monthsOf([row('2024-01-31'), row('2024-02-15'), row('2024-02-29')], LATER)
    expect(months[0].coverage).toBe('')
  })

  it('keeps the latest month provisional while it closes today', () => {
    const [month] = monthsOf([row('2025-11-30'), row('2025-12-31', 10)], '2025-12-31')
    expect([month.coverage, month.detail.slice(0, 6)]).toEqual(['to 31 Dec', 'Now at'])
  })

  it('closes stale latest data at its own date', () => {
    const [month] = monthsOf([row('2026-09-30'), row('2026-10-12', 10)], '2026-10-14')
    expect([month.coverage, month.detail.slice(0, 9)]).toEqual(['to 12 Oct', 'Closed at'])
  })
})

describe('gaps', () => {
  it('says so when an earlier month closes early and the next one covers its rest', () => {
    const [july, august] = monthsOf(
      [row('2021-06-30'), row('2021-07-28', 10), row('2021-08-31', 20)],
      LATER
    )
    expect([
      july.coverage,
      july.notes.includes('No daily summary after 28 Jul'),
      august.isSingleMonth,
      august.coverage,
      august.notes.includes('Covers 2 months'),
    ]).toEqual(['to 28 Jul', true, false, '29 Jul – 31 Aug', true])
  })

  it('covers a whole missing month with the next observed one', () => {
    const months = monthsOf(
      [
        row('2021-06-30'),
        ...everyDay('2021-07-01', '2021-07-31'),
        ...everyDay('2021-09-01', '2021-09-30'),
      ],
      LATER
    )
    expect(months.map(month => [month.key, month.notes])).toEqual([
      ['2021-07', []],
      ['2021-09', ['Covers 2 months, no daily summary for Aug 2021']],
    ])
  })

  it('names the missing days inside a month', () => {
    const rows = everyDay('2025-11-30', '2025-12-31').filter(day => day.date !== '2025-12-30')
    expect(monthsOf(rows, LATER)[0].notes).toEqual(['No daily summary for 30 Dec'])
  })

  it('counts the missing days once there are more than three gaps', () => {
    const kept = ['2025-11-30', '2025-12-02', '2025-12-04', '2025-12-06', '2025-12-08']
    const rows = [...kept.map(date => row(date)), ...everyDay('2025-12-10', '2025-12-31')]
    expect(monthsOf(rows, LATER)[0].notes).toEqual(['No daily summary for 5 days'])
  })
})

describe('euro change and percentage', () => {
  it('rounds the endpoint difference once', () => {
    const rows = [
      row('2025-01-31', 0),
      row('2025-02-01', 0.004),
      row('2025-02-02', 0.008),
      row('2025-02-03', 0.012),
    ]
    expect(monthsOf(rows, LATER)[0].change).toBe(0.01)
  })

  it('keeps the daily percentage convention through a deposit', () => {
    const [month] = monthsOf([row('2025-01-31', 0, 1000), row('2025-02-01', 100, 2100)], LATER)
    expect(month.percent).toBeCloseTo(5, 10)
  })

  it('lets the euros and the percentage disagree in sign', () => {
    const [month] = monthsOf(
      [row('2025-01-31', 0, 1000), row('2025-02-01', -40, 960), row('2025-02-02', 60, 10100)],
      LATER
    )
    expect([month.change, month.cell, month.step]).toEqual([60, '−3.0', 'loss-3'])
  })

  it('keeps a non-zero percentage beside zero euros', () => {
    const [month] = monthsOf(
      [row('2025-01-31', 0, 1000), row('2025-02-01', 50, 1050), row('2025-02-02', 0, 10000)],
      LATER
    )
    expect([month.change, month.cell, month.step]).toEqual([0, '+4.5', 'gain-3'])
  })

  it('has no percentage over a non-positive base and keeps the euros', () => {
    const [month] = monthsOf([row('2025-01-31', 0, 0), row('2025-02-28', 10, 10)], LATER)
    expect([
      month.percent,
      month.change,
      month.cell,
      month.notes.includes('Percentage unavailable'),
    ]).toEqual([null, 10, '—', true])
  })

  it('never turns a non-finite value into a zero return', () => {
    const [month] = monthsOf([row('2025-01-31'), row('2025-02-28', 10, Number.NaN)], LATER)
    expect([month.percent, month.name]).toEqual([
      null,
      'February 2025, +€10.00, Percentage unavailable',
    ])
  })

  it('names a month by its label, coverage, euros and percentage', () => {
    const [month] = monthsOf([row('2025-06-27', 0, 1000), row('2025-06-30', 12.34, 1012.34)], LATER)
    expect(month.name).toBe('June 2025, from 27 Jun, +€12.34, +1.2%')
  })
})

describe('colour steps and cell text', () => {
  it.each([
    [0.24, 'flat'],
    [0.26, 'gain-1'],
    [0.3, 'gain-1'],
    [0.94, 'gain-1'],
    [0.96, 'gain-2'],
    [2.5, 'gain-3'],
    [4.96, 'gain-4'],
    [-5, 'loss-4'],
    [-0.26, 'loss-1'],
    [-0.04, 'flat'],
  ])('puts %s percent in the %s step whatever the euros say', (percent, step) => {
    expect(stepOfMonth(-percent, percent)).toBe(step)
  })

  it('falls back to the side of the euros without a percentage', () => {
    expect([stepOfMonth(-3, null), stepOfMonth(0, null), stepOfMonth(2, null)]).toEqual([
      'loss-1',
      'flat',
      'gain-1',
    ])
  })

  it('drops the decimals from 100 and prints no sign on a rounded zero', () => {
    expect([-0.04, 123.44, -250.5, 8.04, 99.96, null].map(cellTextOf)).toEqual([
      '0.0',
      '+123',
      '−251',
      '+8.0',
      '+100',
      '—',
    ])
  })
})

describe('monthly model', () => {
  const sparse = () =>
    buildMonthlyProfit(
      [row('2021-06-30'), row('2021-07-31', 100, 1100), row('2021-09-30', 150, 1150)],
      LATER
    )!

  it('has no result for zero, one or one repeated observation', () => {
    expect([
      buildMonthlyProfit([], LATER),
      buildMonthlyProfit([row('2025-01-01')], LATER),
      buildMonthlyProfit([row('2025-01-01'), row('2025-01-01', 5)], LATER),
    ]).toEqual([null, null, null])
  })

  it('draws a whole missing month as a named slot and leaves the outside empty', () => {
    const [year] = sparse().years
    expect([
      year.slots.length,
      year.slots[5],
      year.slots[7],
      year.slots[9],
      sparse().months.map(month => month.key),
    ]).toEqual([
      12,
      null,
      { kind: 'missing', key: '2021-08', name: 'August 2021, no daily summary' },
      null,
      ['2021-07', '2021-09'],
    ])
  })

  it('leaves a cross-month interval out of the statistics and says single months', () => {
    const monthly = sparse()
    expect([
      monthly.excluded,
      monthly.upMonths,
      monthly.averageMonth,
      monthly.months.map(month => month.rank),
    ]).toEqual([1, 1, 100, ['Best of 1 single month', '']])
  })

  it('divides the unrounded total once for the average', () => {
    const monthly = buildMonthlyProfit(
      [
        row('2025-01-31', 0),
        row('2025-02-28', 0.006),
        row('2025-03-31', 0.012),
        row('2025-04-30', 0.013),
      ],
      LATER
    )!
    expect([monthly.months.map(month => month.change), monthly.averageMonth]).toEqual([
      [0.01, 0.01, 0],
      0,
    ])
  })

  it('has no average without a single-month interval', () => {
    const monthly = buildMonthlyProfit([row('2025-01-15'), row('2025-03-31', 10)], LATER)!
    expect([monthly.averageMonth, monthly.excluded]).toEqual([null, 1])
  })

  it('shares a rank between equal changes and takes the later one as best', () => {
    const monthly = buildMonthlyProfit(
      [
        row('2025-01-31', 0),
        row('2025-02-28', 100, 1100),
        row('2025-03-31', 200, 1200),
        row('2025-04-30', 250, 1250),
      ],
      LATER
    )!
    expect([monthly.months.map(month => month.rank), monthly.best?.key]).toEqual([
      ['Best of 3 months', 'Best of 3 months', '3rd best of 3 months'],
      '2025-03',
    ])
  })

  it('gives a zero change no rank and no extreme', () => {
    const monthly = buildMonthlyProfit([row('2025-01-31'), row('2025-02-28')], LATER)!
    expect([monthly.months[0].rank, extremeMonths(monthly), monthly.flatMonths]).toEqual([
      '',
      [],
      1,
    ])
  })

  it('names the best and the worst month for the shortcuts', () => {
    const monthly = buildMonthlyProfit(
      [row('2025-01-31', 0), row('2025-02-28', -50, 950), row('2025-03-31', 20, 1020)],
      LATER
    )!
    expect(
      extremeMonths(monthly).map(({ label, variant, month }) => [label, variant, month.key])
    ).toEqual([
      ['Best month', 'gain-4', '2025-03'],
      ['Worst month', 'loss-4', '2025-02'],
    ])
  })
})

describe('year totals', () => {
  it('has no result for a year entered without a year-end row and keeps the year before', () => {
    const { years } = buildMonthlyProfit(
      [
        row('2024-11-30', 0),
        row('2024-12-20', 10, 1010),
        row('2025-01-31', 30, 1030),
        row('2025-02-28', 40, 1040),
      ],
      LATER
    )!
    expect(years.map(year => [year.year, year.change, year.percent === null, year.note])).toEqual([
      [2025, null, true, 'Year result unavailable: missing year-end summary'],
      [2024, 10, false, '2024 from 30 Nov to 20 Dec'],
    ])
  })

  it('draws a wholly missing year as twelve missing slots without a result', () => {
    const { years } = buildMonthlyProfit(
      [row('2021-11-30'), row('2021-12-31', 10, 1010), row('2023-01-31', 20, 1020)],
      LATER
    )!
    expect([
      years.map(year => year.year),
      years[1].slots.every(slot => slot?.kind === 'missing'),
      years[1].change,
      years[1].percent,
    ]).toEqual([[2023, 2022, 2021], true, null, null])
  })

  it('has no year percentage when one month has none and keeps the euros', () => {
    const [year] = buildMonthlyProfit(
      [row('2024-12-31', 0, 1000), row('2025-01-31', 10, 10), row('2025-02-28', 20, 1020)],
      LATER
    )!.years
    expect([year.change, year.percent]).toEqual([20, null])
  })

  it('compounds the unrounded factors of its months', () => {
    const [year] = buildMonthlyProfit(
      [row('2024-12-31', 0, 1000), row('2025-01-31', 100, 1100), row('2025-02-28', 210, 1210)],
      LATER
    )!.years
    expect([year.percent?.toFixed(6), year.note, year.name]).toEqual([
      '21.000000',
      '2025 to 28 Feb',
      '2025 to 28 Feb, +€210.00, +21.0%',
    ])
  })

  it('labels an unfinished year to its closing date', () => {
    const [year] = buildMonthlyProfit(
      [row('2025-12-31'), row('2026-01-31', 10, 1010), row('2026-10-12', 20, 1020)],
      '2026-10-14'
    )!.years
    expect(year.note).toBe('2026 to 12 Oct')
  })
})

describe('month navigation', () => {
  const monthly = buildMonthlyProfit(
    [
      row('2019-12-31'),
      row('2020-01-31', 10, 1010),
      row('2021-07-31', 20, 1020),
      row('2021-09-30', 30, 1030),
      row('2022-01-31', 40, 1040),
    ],
    LATER
  )!

  it('skips missing months in steps of the same size', () => {
    expect([
      moveMonth(monthly, '2021-07', 1),
      moveMonth(monthly, '2021-09', -1),
      moveMonth(monthly, '2022-01', -12),
      moveMonth(monthly, '2020-01', 12),
    ]).toEqual(['2021-09', '2021-07', '2020-01', '2022-01'])
  })

  it('stays put when no selectable month lies that way', () => {
    expect([
      moveMonth(monthly, '2020-01', -12),
      moveMonth(monthly, '2021-07', 12),
      moveMonth(monthly, '2022-01', 1),
    ]).toEqual([null, null, null])
  })

  it('finds a month by its key', () => {
    expect([monthOn(monthly, '2021-09')?.key, monthOn(monthly, '2021-08')]).toEqual([
      '2021-09',
      undefined,
    ])
  })
})
