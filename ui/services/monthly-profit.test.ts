import { afterEach, describe, it, expect, vi } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow as row } from '../tests/fixtures'
import { addDays, daysBetween } from '../utils/iso-dates'
import { buildMonthlyProfit, monthOn, moveMonth, NOT_ENOUGH_HISTORY } from './monthly-profit'

const TODAY = '2030-01-01'

const build = (rows: PortfolioSummaryDto[], today = TODAY) => buildMonthlyProfit(rows, today)!

const series = (first: string, last: string, profitPerDay: number): PortfolioSummaryDto[] =>
  Array.from({ length: daysBetween(first, last) + 1 }, (_, index) =>
    row(addDays(first, index), index * profitPerDay)
  )

describe('aggregation', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('returns null for zero or one observation', () =>
    expect([
      buildMonthlyProfit([], TODAY),
      buildMonthlyProfit([row('2025-03-31', 0)], TODAY),
    ]).toEqual([null, null]))

  it('returns null when every row has the same date', () =>
    expect(buildMonthlyProfit([row('2025-03-31', 0), row('2025-03-31', 10)], TODAY)).toBeNull())

  it('explains why there is no model', () =>
    expect(NOT_ENOUGH_HISTORY).toBe(
      'Not enough daily summary history to calculate monthly changes.'
    ))

  it('ignores input order', () => {
    const shuffled = series('2024-12-31', '2025-03-31', 10).sort((a, b) =>
      a.date.slice(8).localeCompare(b.date.slice(8))
    )

    expect(build(shuffled).months.map(month => [month.key, month.change])).toEqual([
      ['2025-01', 310],
      ['2025-02', 280],
      ['2025-03', 310],
    ])
  })

  it('lets the later duplicate date win', () =>
    expect(
      monthOn(
        build([row('2025-02-28', 0), row('2025-03-31', 10), row('2025-03-31', 20)]),
        '2025-03'
      )?.change
    ).toBe(20))

  it('starts at the month after a month-end baseline', () =>
    expect(
      build([row('2025-01-31', 0), row('2025-02-01', 10), row('2025-02-28', 30)]).months.map(
        month => [month.key, month.coverage, month.change]
      )
    ).toEqual([['2025-02', '', 30]]))

  it('groups ISO dates the same in a time zone behind UTC', () => {
    vi.stubEnv('TZ', 'America/Los_Angeles')

    expect(
      build([row('2025-06-30', 0), row('2025-07-01', 10)]).months.map(month => [
        month.key,
        month.label,
      ])
    ).toEqual([['2025-07', 'Jul 2025']])
  })
})

describe('coverage', () => {
  it('closes leap February on the 29th', () =>
    expect(monthOn(build([row('2024-01-31', 0), row('2024-02-29', 10)]), '2024-02')?.coverage).toBe(
      ''
    ))

  it('labels a first month that starts mid-month', () => {
    const model = build([row('2025-06-27', 0), row('2025-06-30', 10), row('2025-07-31', 20)])
    const june = monthOn(model, '2025-06')

    expect([june?.coverage, june?.isSingle]).toEqual(['from 27 Jun', true])
  })

  it('names the opening year when the interval starts in an earlier year', () =>
    expect(build([row('2014-11-19', 0), row('2015-01-31', 10)]).months[0].coverage).toBe(
      'from 19 Nov 2014'
    ))

  it('labels a stale latest month', () => {
    const october = build(series('2025-09-30', '2025-10-12', 10), '2025-10-20').months[0]

    expect([october.coverage, october.closing, october.note]).toEqual([
      'to 12 Oct',
      'Closed at €100,000.00',
      '',
    ])
  })

  it("treats today's month-end row as provisional", () => {
    const december = build(series('2025-11-30', '2025-12-31', 10), '2025-12-31').months[0]

    expect([december.coverage, december.closing]).toEqual(['to 31 Dec', 'Now at €100,000.00'])
  })
})

describe('gaps', () => {
  it('flags an early close and the two-month interval after it', () => {
    const model = build([...series('2025-06-30', '2025-07-28', 10), row('2025-08-31', 500)])
    const [july, august] = model.months

    expect([
      july.coverage,
      july.note,
      july.detail,
      august.coverage,
      august.note,
      august.isSingle,
      august.name,
      model.excluded,
    ]).toEqual([
      'to 28 Jul',
      'No daily summary after 28 Jul',
      'Best of 1 single month · closed at €100,000.00 · XIRR 20.69%',
      'from 28 Jul',
      'Covers 2 months, no daily summary for 29 Jul to 30 Aug',
      false,
      'August 2025, from 28 Jul, +€220.00, +0.2%',
      '1 month not counted, covering more than one month',
    ])
  })

  it('flags missing days inside a month', () => {
    const rows = series('2025-11-30', '2025-12-09', 10).filter(({ date }) => date !== '2025-12-05')
    const model = build(rows)

    expect([model.months[0].note, model.upMonths + model.downMonths + model.flatMonths]).toEqual([
      'No daily summary for 5 Dec',
      1,
    ])
  })

  it('skips a wholly missing month', () => {
    const model = build([
      row('2021-07-30', 0),
      row('2021-07-31', 10),
      row('2021-09-01', 20),
      row('2021-09-30', 30),
    ])
    const september = monthOn(model, '2021-09')

    expect([
      model.months.map(month => month.key),
      september?.coverage,
      september?.note,
      moveMonth(model, '2021-07', 1),
    ]).toEqual([
      ['2021-07', '2021-09'],
      'from 31 Jul',
      'Covers 2 months, no daily summary for 1 to 31 Aug · No daily summary for 2 to 29 Sep',
      '2021-09',
    ])
  })
})
