import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import {
  createCalendarRow as row,
  createMissingMonthRows,
  createMonthlyProfit,
} from '../tests/fixtures'
import { formatMonthCell } from './monthly-profit'
import { hasMissingMonth, monthlyYears, yearNotes } from './monthly-profit-years'

const TODAY = '2030-01-01'
const UNAVAILABLE = 'Year result unavailable: missing year-end summary'

const yearsOf = (rows: PortfolioSummaryDto[], today = TODAY) =>
  monthlyYears(createMonthlyProfit(rows, today))

describe('monthly years', () => {
  it('draws a missing month inside a year', () => {
    const years = yearsOf(createMissingMonthRows())
    expect([years[0].slots[7], years[0].name, years[0].text, hasMissingMonth(years)]).toEqual([
      { kind: 'missing', key: '2021-08', name: 'August 2021, no daily summary' },
      '2021, from 30 Jun to 30 Sep, +€200.00, +0.2%',
      '0.2%',
      true,
    ])
  })

  it('leaves a wholly missing year without a result', () => {
    const years = yearsOf([row('2019-11-30', 0), row('2019-12-31', 10), row('2021-01-31', 20)])
    const [latest, empty] = years
    expect([
      years.map(year => year.year),
      empty.slots.map(slot => slot?.kind),
      empty.text,
      empty.name,
      latest.text,
      latest.note,
    ]).toEqual([
      ['2021', '2020', '2019'],
      Array(12).fill('missing'),
      '—',
      '2020, no daily summary',
      '—',
      UNAVAILABLE,
    ])
  })

  it('cannot isolate a year without a year-end summary', () => {
    const [latest, earlier] = yearsOf([
      row('2024-11-30', 0),
      row('2024-12-15', 10),
      row('2025-01-31', 20),
    ])
    expect([latest.text, latest.note, latest.name, earlier.coverage]).toEqual([
      '—',
      UNAVAILABLE,
      '2025, year result unavailable: missing year-end summary',
      'from 30 Nov to 15 Dec',
    ])
  })

  it('starts a year from a 31 December baseline', () => {
    const years = yearsOf([row('2024-12-31', 0), row('2025-01-31', 100)])
    expect(years.map(year => year.name)).toEqual(['2025, to 31 Jan, +€100.00, +0.1%'])
  })

  it('compounds unrounded month factors', () => {
    const model = createMonthlyProfit(
      [row('2024-12-31', 0, 10000), row('2025-01-31', 4, 10004), row('2025-02-28', 8, 10008)],
      TODAY
    )
    expect([
      model.months.map(month => formatMonthCell(month.percent)),
      monthlyYears(model)[0].text,
    ]).toEqual([['0.0', '0.0'], '0.1%'])
  })

  it('leaves the year percentage unavailable when a month percentage is', () => {
    const [year] = yearsOf([
      row('2024-12-31', 0, 100),
      row('2025-01-31', 200, 150),
      row('2025-02-28', 300, 300),
    ])
    expect([year.text, year.note, year.name]).toEqual([
      '—',
      'Percentage unavailable',
      '2025, to 28 Feb, +€300.00, percentage unavailable',
    ])
  })

  it('keys each year note by its year', () =>
    expect(
      yearNotes(
        yearsOf([
          row('2024-12-31', 0, 100),
          row('2025-01-31', 200, 150),
          row('2025-02-28', 300, 300),
        ])
      )
    ).toEqual(['2025 · to 28 Feb · Percentage unavailable']))

  it('labels an unfinished latest year', () => {
    const [year] = yearsOf([row('2025-12-31', 0), row('2026-09-30', 50), row('2026-10-16', 100)])
    expect(year.coverage).toBe('to 16 Oct')
  })

  it('treats a year-end row dated today as provisional', () => {
    const [year] = yearsOf(
      [row('2024-12-31', 0), row('2025-11-30', 50), row('2025-12-31', 100)],
      '2025-12-31'
    )
    expect(year.coverage).toBe('to 31 Dec')
  })
})
