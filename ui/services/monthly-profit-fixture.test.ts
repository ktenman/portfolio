import { describe, expect, it } from 'vitest'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { buildMonthlyProfit, formatMonthlyPercent, moveMonth } from './monthly-profit'

describe('monthly profit of the visual fixture', () => {
  const rows = [...SERIES_RESPONSE, TODAY_SUMMARY]
  const build = () => {
    const calendar = buildMonthlyProfit(rows, '2026-01-15')
    expect(calendar).not.toBeNull()
    if (!calendar) throw new Error('Expected observed monthly profit')
    return calendar
  }

  it('calculates seven observed months from the initial baseline', () => {
    const calendar = build()

    expect(new Set(rows.map(row => row.date)).size).toBe(187)
    expect(calendar.months[0].opening.date).toBe('2025-06-27')
    expect(calendar.months.map(month => [month.key, month.change])).toEqual([
      ['2025-06', 455.37],
      ['2025-07', 3993.96],
      ['2025-08', 9177.8],
      ['2025-09', 5303.35],
      ['2025-10', 3995.68],
      ['2025-11', 12307.39],
      ['2025-12', 9110.23],
    ])
  })

  it('matches every independently specified compounded monthly percentage', () => {
    expect(build().months.map(month => formatMonthlyPercent(month.percent))).toEqual([
      '+0.4%',
      '+3.8%',
      '+8.0%',
      '+4.0%',
      '+2.9%',
      '+8.3%',
      '+5.4%',
    ])
  })

  it('matches the monthly figures and observed year result', () => {
    const calendar = build()

    expect([
      calendar.upMonths,
      calendar.downMonths,
      calendar.flatMonths,
      calendar.averageMonth,
      calendar.excludedMonths,
    ]).toEqual([7, 0, 0, 6334.83, 0])
    expect(calendar.years[0].change).toBe(44343.78)
    expect(formatMonthlyPercent(calendar.years[0].percent)).toBe('+37.6%')
    expect(calendar.years[0].coverage).toContain('from 27 Jun 2025')
  })

  it('keeps December snapshot estimates unchanged while ranking its actual month change', () => {
    const december = build().months[6]

    expect(december.closing).toBe(TODAY_SUMMARY)
    expect(december.closing.totalValue).toBe(178204.06)
    expect(december.closing.xirrAnnualReturn).toBe(0.2118)
    expect(december.closing.earningsPerMonth).toBe(3363.8)
    expect(december.closing.totalProfitChange24h).toBeNull()
    expect(december.rank).toBe('3rd best of 7 months')
    expect(december.name).toContain('XIRR 21.18%')
  })

  it('exposes both the starting coverage and the missing 30 December observation', () => {
    const calendar = build()

    expect(calendar.months[0].coverage).toContain('from 27 Jun')
    expect(calendar.months[6].coverage).toContain('No daily summary for 30 Dec 2025')
    expect(calendar.months[6].name).toContain('No daily summary for 30 Dec 2025')
    expect(calendar.months[6].closingLabel).toBe('Closed at €178,204.06 · as of 31 Dec 2025')
  })

  it('finds November as best with no worst shortcut and cannot move beyond the observed ends', () => {
    const calendar = build()

    expect(calendar.best?.key).toBe('2025-11')
    expect(calendar.worst).toBeNull()
    expect(moveMonth(calendar, '2025-06', -1)).toBeNull()
    expect(moveMonth(calendar, '2025-12', 1)).toBeNull()
    expect(calendar.span).toBe('Jun – Dec 2025')
  })

  it('shows year-level coverage while retaining missing-day detail in the accessible year name', () => {
    const year = build().years[0]

    expect(year.coverage).toEqual(['from 27 Jun 2025'])
    expect(year.name).toContain('No daily summary for 30 Dec 2025')
  })
})
