import { describe, it, expect } from 'vitest'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import { buildMonthlyProfit, extremeMonths, monthOn, moveMonth } from './monthly-profit'
import { mergeHistoricalWithCurrent } from './summary-aggregator'

describe('monthly profit of the visual fixture', () => {
  const model = buildMonthlyProfit(
    mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY),
    '2026-01-15'
  )!
  const december = monthOn(model, '2025-12')!

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
})
