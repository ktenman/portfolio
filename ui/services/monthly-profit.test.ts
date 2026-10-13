import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays, daysBetween } from '../utils/iso-dates'
import { monthsOf } from './monthly-profit'
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
    const months = monthsOf([row('2025-01-31'), row('2025-02-01', 10), row('2025-02-28', 20)], LATER)
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
    const [month] = monthsOf(
      [row('2025-06-27', 0, 1000), row('2025-06-30', 12.34, 1012.34)],
      LATER
    )
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
