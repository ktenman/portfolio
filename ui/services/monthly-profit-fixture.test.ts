import { describe, it, expect } from 'vitest'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { buildMonthlyProfit, monthOn, moveMonth } from './monthly-profit'
import { mergeSummariesByDate } from './summary-aggregator'

describe('monthly profit of the visual fixture', () => {
  const monthly = buildMonthlyProfit(
    mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]),
    '2026-01-15'
  )!

  it('earns the reference euros and percentages in each of its seven months', () => {
    expect(monthly.months.map(month => [month.key, month.change, month.cell])).toEqual([
      ['2025-06', 455.37, '+0.4'],
      ['2025-07', 3993.96, '+3.8'],
      ['2025-08', 9177.8, '+8.0'],
      ['2025-09', 5303.35, '+4.0'],
      ['2025-10', 3995.68, '+2.9'],
      ['2025-11', 12307.39, '+8.3'],
      ['2025-12', 9110.23, '+5.4'],
    ])
  })

  it('counts seven up months averaging +€6,334.83 with November the best and no worst', () => {
    expect([
      monthly.upMonths,
      monthly.downMonths,
      monthly.flatMonths,
      monthly.averageMonth,
      monthly.excluded,
      monthly.span,
      monthly.best?.key,
      monthly.worst,
    ]).toEqual([7, 0, 0, 6334.83, 0, 'Jun – Dec 2025', '2025-11', null])
  })

  it('totals 2025 from its 27 June baseline', () => {
    const [year] = monthly.years
    expect([year.year, year.change, year.percent?.toFixed(1), year.note]).toEqual([
      2025,
      44343.78,
      '37.6',
      '2025 from 27 Jun',
    ])
  })

  it('ranks December third with its closing snapshot and its missing day', () => {
    const december = monthOn(monthly, '2025-12')!
    expect([
      december.rank,
      december.closing.totalValue,
      december.notes,
      december.coverage,
      december.detail,
    ]).toEqual([
      '3rd best of 7 months',
      178204.06,
      ['No daily summary for 30 Dec'],
      '',
      '3rd best of 7 months · closed at €178,204.06 · XIRR 21.18%',
    ])
  })

  it('labels June from its baseline and stops the arrows at both ends', () => {
    expect([
      monthOn(monthly, '2025-06')!.coverage,
      moveMonth(monthly, '2025-06', -1),
      moveMonth(monthly, '2025-12', 1),
      moveMonth(monthly, '2025-06', 1),
    ]).toEqual(['from 27 Jun', null, null, '2025-07'])
  })
})
