import { describe, expect, it } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow as row } from '../tests/fixtures'
import { buildMonthlyProfit } from './monthly-profit'

const build = (rows: PortfolioSummaryDto[], today = '2030-01-01') => {
  const calendar = buildMonthlyProfit(rows, today)
  expect(calendar).not.toBeNull()
  if (!calendar) throw new Error('Expected observed monthly profit')
  return calendar
}

describe('month coverage', () => {
  it('combines the baseline and current closing date for a partial first month', () => {
    const month = build(
      [row('2025-06-27', 0), row('2025-06-28', 100), row('2025-06-29', 200)],
      '2025-06-29'
    ).months[0]

    expect(month.eligible).toBe(true)
    expect(month.coverage).toContain('from 27 Jun to 29 Jun')
    expect(month.isCurrent).toBe(true)
    expect(month.closingLabel).toBe('Now at €100,000.00')
    expect(month.name).toContain('from 27 Jun to 29 Jun')
  })

  it('keeps an intraday month-end closing provisional', () => {
    const month = build([row('2025-11-30', 0), row('2025-12-31', 100)], '2025-12-31').months[0]

    expect(month.coverage).toContain('to 31 Dec')
    expect(month.isCurrent).toBe(true)
    expect(month.closingLabel).toBe('Now at €100,000.00')
  })

  it('does not treat a historical month-end closing as current', () => {
    const month = build([row('2025-11-30', 0), row('2025-12-31', 100)], '2026-01-15').months[0]

    expect(month.coverage.some(note => note.startsWith('to '))).toBe(false)
    expect(month.isCurrent).toBe(false)
    expect(month.closingLabel).toBe('Closed at €100,000.00 · as of 31 Dec 2025')
  })

  it('exposes the as-of date and unobserved tail when the latest snapshot is stale', () => {
    const month = build([row('2025-09-30', 0), row('2025-10-12', 100)], '2025-10-13').months[0]

    expect(month.coverage).toContain('to 12 Oct')
    expect(month.coverage).toContain('No daily summary after 12 Oct')
    expect(month.closingLabel).toContain('as of 12 Oct 2025')
    expect(month.name).toContain('No daily summary after 12 Oct')
  })

  it('flags missing observations inside a month without excluding its interval', () => {
    const month = build([row('2025-06-27', 0, 1000), row('2025-06-30', 100, 1100)]).months[0]

    expect(month.eligible).toBe(true)
    expect(month.change).toBe(100)
    expect(month.percent).toBeCloseTo(10, 10)
    expect(month.coverage).toContain('from 27 Jun')
    expect(month.coverage).toContain('No daily summary for 28 Jun 2025 – 29 Jun 2025')
  })

  it('excludes an adjacent interval that also covers the earlier month tail', () => {
    const calendar = build([row('2021-06-30', 0), row('2021-07-28', 100), row('2021-08-31', 300)])
    const [july, august] = calendar.months

    expect(july.coverage).toContain('to 28 Jul')
    expect(july.coverage).toContain('No daily summary after 28 Jul')
    expect(august.eligible).toBe(false)
    expect(august.coverage).toContain('from 28 Jul 2021 to 31 Aug 2021')
    expect(august.coverage).toContain('Covers 2 months')
    expect(august.coverage).toContain('No daily summary for 29 Jul 2021 – 30 Aug 2021')
    expect(august.name).toContain('Covers 2 months')
    expect(calendar.excludedMonths).toBe(1)
    expect(calendar.averageMonth).toBe(100)
    expect(august.rank).toBe('')
  })

  it('distinguishes whole missing months from positions outside the observed span', () => {
    const calendar = build([row('2021-07-31', 0), row('2021-08-31', 100), row('2021-10-31', 300)])
    const slots = calendar.years[0].slots

    expect(calendar.months.map(month => month.key)).toEqual(['2021-08', '2021-10'])
    expect(slots).toHaveLength(12)
    expect(slots[6]).toMatchObject({ kind: 'empty', key: '2021-07' })
    expect(slots[7]).toMatchObject({ kind: 'observed', key: '2021-08' })
    expect(slots[8]).toEqual({
      kind: 'missing',
      key: '2021-09',
      name: 'September 2021, no daily summary',
    })
    expect(slots[10]).toMatchObject({ kind: 'empty', key: '2021-11' })
    expect(calendar.months[1].coverage).toContain('Covers 2 months')
    expect(calendar.months[1].eligible).toBe(false)
  })

  it('uses the true last day of leap February', () => {
    const calendar = build([row('2024-01-31', 0), row('2024-02-29', 100), row('2024-03-01', 150)])

    expect(calendar.months[0].coverage.some(note => note.startsWith('to '))).toBe(false)
    expect(calendar.months[1].opening.date).toBe('2024-02-29')
    expect(calendar.months[1].eligible).toBe(true)
  })
})

describe('year coverage and results', () => {
  it('orders years newest first and keeps their slots January through December', () => {
    const calendar = build([row('2024-11-30', 0), row('2024-12-31', 100), row('2025-01-31', 300)])

    expect(calendar.years.map(year => year.year)).toEqual([2025, 2024])
    expect(calendar.years[0].slots.map(slot => slot.key)).toEqual([
      '2025-01',
      '2025-02',
      '2025-03',
      '2025-04',
      '2025-05',
      '2025-06',
      '2025-07',
      '2025-08',
      '2025-09',
      '2025-10',
      '2025-11',
      '2025-12',
    ])
    expect(calendar.years[0].slots[0].kind).toBe('observed')
  })

  it('calculates year euros from its first opening and last closing even across a same-year gap', () => {
    const calendar = build([
      row('2024-12-31', 0, 1000),
      row('2025-01-31', 100, 1100),
      row('2025-04-30', 500, 1500),
      row('2025-12-31', 1000, 2000),
    ])
    const year = calendar.years[0]

    expect(year.change).toBe(1000)
    expect(year.percent).toBeCloseTo(100, 10)
    expect(year.name).toContain('+€1,000.00')
    expect(year.name).toContain('+100.0%')
  })

  it('compounds unrounded factors instead of displayed monthly percentages', () => {
    const year = build([
      row('2025-01-31', 0, 100),
      row('2025-02-28', 0.04, 100.04),
      row('2025-03-31', 0.08, 100.08),
    ]).years[0]

    expect(year.change).toBe(0.08)
    expect(year.percent).toBeCloseTo(0.08, 10)
    expect(year.name).toContain('+0.1%')
  })

  it('does not isolate a later year when its opening interval crosses a missing year end', () => {
    const calendar = build([
      row('2025-11-30', 0, 1000),
      row('2025-12-30', 100, 1100),
      row('2026-01-31', 300, 1300),
    ])
    const [later, earlier] = calendar.years

    expect([later.change, later.percent]).toEqual([null, null])
    expect(later.coverage).toContain('Year result unavailable: missing year-end summary')
    expect(later.name).toContain('Year result unavailable: missing year-end summary')
    expect(earlier.change).toBe(100)
    expect(calendar.months[1].eligible).toBe(false)
    expect(calendar.months[1].coverage).toContain('Covers 2 months')
  })

  it('isolates the later year when the opening snapshot is 31 December', () => {
    const calendar = build([
      row('2025-11-30', 0, 1000),
      row('2025-12-31', 100, 1100),
      row('2026-01-31', 300, 1300),
    ])

    expect(calendar.years[0].change).toBe(200)
    expect(calendar.years[0].percent).toBeCloseTo(18.1818181818, 9)
    expect(calendar.months[1].eligible).toBe(true)
  })

  it('gives a wholly missing year no result and no observed table months', () => {
    const calendar = build([row('2020-12-31', 0), row('2021-01-31', 100), row('2023-03-01', 500)])
    const missing = calendar.years[1]

    expect(calendar.years.map(year => year.year)).toEqual([2023, 2022, 2021])
    expect([missing.change, missing.percent]).toEqual([null, null])
    expect(missing.slots.every(slot => slot.kind === 'missing')).toBe(true)
    expect(missing.name).toContain('no daily summary')
    expect(calendar.months.map(month => month.key)).toEqual(['2021-01', '2023-03'])
    expect(calendar.months[1].coverage).toContain('Covers 26 months')
  })

  it('labels the first partial year and the unfinished latest year', () => {
    const year = build(
      [row('2025-06-27', 0), row('2025-06-30', 100), row('2025-10-12', 400)],
      '2025-10-13'
    ).years[0]

    expect(year.coverage).toContain('from 27 Jun 2025')
    expect(year.coverage).toContain('YTD to 12 Oct 2025')
    expect(year.name).toContain('from 27 Jun 2025')
    expect(year.name).toContain('YTD to 12 Oct 2025')
  })

  it('keeps a year ending today provisional even on 31 December', () => {
    const year = build([row('2024-12-31', 0), row('2025-12-31', 100)], '2025-12-31').years[0]

    expect(year.coverage).toContain('YTD to 31 Dec 2025')
    expect(year.change).toBe(100)
  })

  it('makes the whole year percentage unavailable when one required month is unavailable', () => {
    const year = build([
      row('2025-01-31', 0, 1000),
      row('2025-02-28', 100, 100),
      row('2025-03-31', 200, 1200),
    ]).years[0]

    expect(year.change).toBe(200)
    expect(year.percent).toBeNull()
    expect(year.name).toContain('Percentage unavailable')
  })
})
