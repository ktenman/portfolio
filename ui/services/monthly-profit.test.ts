import { describe, expect, it } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow as row, createPortfolioSummaryDto } from '../tests/fixtures'
import { buildMonthlyProfit } from './monthly-profit'

const TODAY = '2030-01-01'
const build = (rows: PortfolioSummaryDto[]) => {
  const calendar = buildMonthlyProfit(rows, TODAY)
  expect(calendar).not.toBeNull()
  if (!calendar) throw new Error('Expected observed monthly profit')
  return calendar
}

describe('monthly observation boundaries', () => {
  it('returns no result with fewer than two unique observations', () => {
    expect([
      buildMonthlyProfit([], TODAY),
      buildMonthlyProfit([row('2025-01-31', 0)], TODAY),
      buildMonthlyProfit([row('2025-01-31', 0), row('2025-01-31', 100)], TODAY),
    ]).toEqual([null, null, null])
  })

  it('groups chronologically without changing input order or daily snapshots', () => {
    const baseline = Object.freeze(row('2025-01-31', 0))
    const february = Object.freeze(row('2025-02-28', 100))
    const march = Object.freeze(row('2025-03-31', 300))
    const rows = [march, baseline, february]
    const calendar = build(rows)

    expect(rows).toEqual([march, baseline, february])
    expect(
      calendar.months.map(month => [month.key, month.opening.date, month.closing.date])
    ).toEqual([
      ['2025-02', '2025-01-31', '2025-02-28'],
      ['2025-03', '2025-02-28', '2025-03-31'],
    ])
    expect(calendar.months[0].opening).toBe(baseline)
    expect(calendar.months[0].closing).toBe(february)
    expect(calendar.months[1].closing).toBe(march)
  })

  it('uses the last occurrence of a duplicate ISO date', () => {
    const replacement = row('2025-02-28', 120)
    const calendar = build([row('2025-02-28', 900), row('2025-01-31', 20), replacement])

    expect(calendar.months).toHaveLength(1)
    expect(calendar.months[0].change).toBe(100)
    expect(calendar.months[0].closing).toBe(replacement)
  })

  it('does not create a result for a baseline on the last day of a month', () => {
    const calendar = build([row('2025-01-31', 0), row('2025-02-01', 40), row('2025-02-28', 100)])

    expect(calendar.months.map(month => month.key)).toEqual(['2025-02'])
    expect(calendar.months[0].eligible).toBe(true)
    expect(calendar.years[0].slots[0]).toMatchObject({ kind: 'empty', key: '2025-01' })
  })

  it('calculates from cumulative profit when daily DTO changes are null or inaccurate', () => {
    const calendar = build([
      createPortfolioSummaryDto({
        date: '2025-01-31',
        totalProfit: 100,
        totalProfitChange24h: 999,
      }),
      createPortfolioSummaryDto({
        date: '2025-02-01',
        totalProfit: 120,
        totalProfitChange24h: null,
      }),
      createPortfolioSummaryDto({
        date: '2025-02-28',
        totalProfit: 150,
        totalProfitChange24h: 999,
      }),
    ])

    expect(calendar.months[0].change).toBe(50)
    expect(calendar.months[0].closing.totalProfitChange24h).toBe(999)
  })

  it('retains every closing snapshot field independently of the monthly change', () => {
    const closing = createPortfolioSummaryDto({
      date: '2025-02-28',
      totalProfit: 300,
      totalValue: 12345,
      unrealizedProfit: 234,
      realizedProfit: 66,
      xirrAnnualReturn: 0.2118,
      earningsPerDay: 5,
      earningsPerMonth: 152.2,
      totalProfitChange24h: 17.5,
    })
    const calendar = build([row('2025-01-31', 100), closing])

    expect(calendar.months[0].change).toBe(200)
    expect(calendar.months[0].closing).toBe(closing)
    expect(calendar.months[0].closing).toEqual({
      date: '2025-02-28',
      totalProfit: 300,
      totalValue: 12345,
      unrealizedProfit: 234,
      realizedProfit: 66,
      xirrAnnualReturn: 0.2118,
      earningsPerDay: 5,
      earningsPerMonth: 152.2,
      totalProfitChange24h: 17.5,
    })
    expect(calendar.months[0].name).toContain('XIRR 21.18%')
  })
})

describe('monthly euro changes and compounded factors', () => {
  it('rounds the endpoint difference once rather than summing rounded daily differences', () => {
    const month = build([
      row('2025-02-01', 0, 1000),
      row('2025-02-02', 0.004, 1000),
      row('2025-02-03', 0.008, 1000),
    ]).months[0]

    expect(month.rawChange).toBe(0.008)
    expect(month.change).toBe(0.01)
    expect(month.percent).toBe(0)
  })

  it('rounds each daily difference to cents before computing its base', () => {
    const month = build([row('2025-02-01', 50.07), row('2025-02-02', 100.07, 100050)]).months[0]

    expect(month.change).toBe(50)
    expect(month.factor).toBeCloseTo(1.0005, 12)
    expect(month.percent).toBeCloseTo(0.05, 10)
  })

  it('colors a compounded loss independently of a positive euro change', () => {
    const month = build([
      row('2025-06-27', 0, 1000),
      row('2025-06-28', -40, 960),
      row('2025-06-29', 60, 10100),
    ]).months[0]

    expect(month.change).toBe(60)
    expect(month.factor).toBeCloseTo(0.9696, 12)
    expect(month.percent).toBeCloseTo(-3.04, 10)
    expect(month.step).toBe('loss-3')
    expect(month.name).toContain('+€60.00, −3.0%')
  })

  it('preserves a nonzero compounded percentage when the euro change is zero', () => {
    const month = build([
      row('2025-06-27', 0, 1000),
      row('2025-06-28', 100, 1100),
      row('2025-06-29', 0, 10000),
    ]).months[0]

    expect(month.change).toBe(0)
    expect(month.percent).toBeCloseTo(8.9108910891, 9)
    expect(month.step).toBe('gain-4')
    expect(month.rank).toBe('')
    expect(month.name).toContain('€0.00, +8.9%')
  })

  it.each([
    [2100, 5],
    [600, 20],
  ])('preserves the daily convention with a closing value of %s', (value, percent) => {
    const month = build([row('2025-06-27', 0, 1000), row('2025-06-28', 100, value)]).months[0]

    expect(month.percent).toBeCloseTo(percent, 10)
  })

  it.each([0, -100, Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY])(
    'keeps the euro change but marks an invalid closing base of %s unavailable',
    base => {
      const calendar = build([row('2025-06-27', 0, 1000), row('2025-06-28', 100, base + 100)])
      const month = calendar.months[0]

      expect([month.change, month.percent, month.factor]).toEqual([100, null, null])
      expect(month.step).toBe('gain-1')
      expect(month.name).toContain('Percentage unavailable')
      expect(calendar.years[0].percent).toBeNull()
    }
  )

  it.each([Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY])(
    'does not derive a percentage from nonfinite cumulative profit %s',
    profit => {
      const calendar = build([row('2025-06-27', profit), row('2025-06-28', 100)])

      expect(calendar.months[0].factor).toBeNull()
      expect(calendar.months[0].percent).toBeNull()
      expect(calendar.years[0].change).toBeNull()
    }
  )

  it('does not substitute zero for an unavailable factor followed by a valid one', () => {
    const month = build([
      row('2025-06-27', 0, 1000),
      row('2025-06-28', 100, 100),
      row('2025-06-29', 150, 1050),
    ]).months[0]

    expect(month.change).toBe(150)
    expect(month.percent).toBeNull()
    expect(month.factor).toBeNull()
  })

  it('marks an overflowed compounded result unavailable', () => {
    const rows = Array.from({ length: 24 }, (_, index) =>
      row(`2025-06-${String(index + 1).padStart(2, '0')}`, -index, -0.9999999999999999)
    )
    const month = build(rows).months[0]

    expect(month.percent).toBeNull()
    expect(month.factor).toBeNull()
  })

  it('normalizes a monthly euro difference rounding to negative zero', () => {
    const month = build([row('2025-06-27', 1), row('2025-06-28', 0.999)]).months[0]

    expect(Object.is(month.change, -0)).toBe(false)
    expect(month.change).toBe(0)
    expect(month.name).toContain('€0.00, 0.0%')
  })

  it('does not promote an unavailable euro change as a best or worst month', () => {
    const calendar = build([row('2025-06-27', 0), row('2025-06-28', Number.POSITIVE_INFINITY)])

    expect(calendar.months[0].name).toContain('Change unavailable')
    expect(calendar.months[0].rank).toBe('')
    expect(calendar.best).toBeNull()
    expect(calendar.worst).toBeNull()
    expect(calendar.averageMonth).toBeNull()
  })

  it('does not let nonfinite euro changes displace a valid month rank', () => {
    const calendar = build([
      row('2025-01-31', 0),
      row('2025-02-28', Number.POSITIVE_INFINITY),
      row('2025-03-31', 0),
      row('2025-04-30', 100),
    ])

    expect(calendar.months[2].rank).toBe('Best of 3 months')
    expect(calendar.best?.key).toBe('2025-04')
    expect(calendar.worst).toBeNull()
  })
})
