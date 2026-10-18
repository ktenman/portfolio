import { describe, it, expect } from 'vitest'
import { createMonthlyProfit } from '../tests/fixtures'
import { TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import { extremeMonths, monthOn, moveMonth } from './monthly-profit'
import { hasMissingMonth, monthlyYears } from './monthly-profit-years'

describe('monthly profit of the visual fixture', () => {
  const model = createMonthlyProfit()
  const december = monthOn(model, '2025-12')!
  const years = monthlyYears(model)

  it('builds the seven reference months', () =>
    expect(
      model.months.map(m => [
        m.label,
        m.coverage,
        formatCurrencyChange(m.change),
        formatPercentChange(m.percent!, 1),
      ])
    ).toEqual([
      ['Jun 2025', 'from 27 Jun', '+€455.37', '+0.4%'],
      ['Jul 2025', '', '+€3,993.96', '+3.8%'],
      ['Aug 2025', '', '+€9,177.80', '+8.0%'],
      ['Sep 2025', '', '+€5,303.35', '+4.0%'],
      ['Oct 2025', '', '+€3,995.68', '+2.9%'],
      ['Nov 2025', '', '+€12,307.39', '+8.3%'],
      ['Dec 2025', '', '+€9,110.23', '+5.4%'],
    ]))

  it('counts 7 up, 0 down and 0 flat months averaging +€6,334.83', () =>
    expect([
      model.upMonths,
      model.downMonths,
      model.flatMonths,
      model.averageMonth,
      model.excluded,
    ]).toEqual([7, 0, 0, 6334.83, '']))

  it('ranks December 3rd best and closes it at €178,204.06 with XIRR 21.18%', () =>
    expect([december.name, december.detail]).toEqual([
      'December 2025, +€9,110.23, +5.4%, 3rd best of 7 months, ranked by euro profit',
      '3rd best of 7 months · closed at €178,204.06 · XIRR 21.18%',
    ]))

  it('notes the missing 30 December', () =>
    expect(december.note).toBe('No daily summary for 30 Dec'))

  it('keeps the closing snapshot unchanged', () => expect(december.row).toBe(TODAY_SUMMARY))

  it('offers November as best and no worst month', () =>
    expect(extremeMonths(model).map(e => [e.label, e.month.key])).toEqual([
      ['Best month', '2025-11'],
    ]))

  it('stops the arrows at June and December', () =>
    expect([moveMonth(model, '2025-06', -1), moveMonth(model, '2025-12', 1)]).toEqual([null, null]))

  it('spans Jun – Dec 2025', () => expect(model.span).toBe('Jun – Dec 2025'))

  it('totals 2025 at +€44,343.78 and +37.6% from 27 Jun', () =>
    expect(years.map(year => [year.year, year.text, year.coverage, year.name])).toEqual([
      ['2025', '37.6%', 'from 27 Jun', '2025, from 27 Jun, +€44,343.78, +37.6%'],
    ]))

  it('places June to December in their calendar slots', () =>
    expect(years[0].slots.map(slot => slot?.key ?? null)).toEqual([
      null,
      null,
      null,
      null,
      null,
      '2025-06',
      '2025-07',
      '2025-08',
      '2025-09',
      '2025-10',
      '2025-11',
      '2025-12',
    ]))

  it('has no missing month', () => expect(hasMissingMonth(years)).toBe(false))
})
