import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MonthlyProfitPanel from './monthly-profit-panel.vue'
import { buildMonthlyProfit, monthOn } from '../../services/monthly-profit'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

const row = (date: string, totalProfit = 0, totalValue = 1000) =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue })

const FIXTURE = mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY])

const createPanel = (
  rows: PortfolioSummaryDto[],
  key: string,
  ends: { previous?: string | null; next?: string | null } = {}
) => {
  const month = monthOn(buildMonthlyProfit(rows, '2026-01-15')!, key)
  if (!month) throw new Error('expected a month')
  return mount(MonthlyProfitPanel, { props: { month, previous: 'p', next: 'n', ...ends } })
}

const textsOf = (wrapper: ReturnType<typeof createPanel>, selector: string) =>
  wrapper.findAll(selector).map(element => element.text())

describe('MonthlyProfitPanel', () => {
  it('shows the month with its euros, percentage, closing line and note', () => {
    const wrapper = createPanel(FIXTURE, '2025-12')
    expect(
      ['.panel-label', '.month-amount', '.month-cents', '.month-percent', '.month-detail'].map(
        selector => wrapper.find(selector).text()
      )
    ).toEqual([
      'December 2025',
      '+€9,110.23',
      '.23',
      '+5.4%',
      '3rd best of 7 months · closed at €178,204.06 · XIRR 21.18%',
    ])
    expect(textsOf(wrapper, '.month-note')).toEqual(['No daily summary for 30 Dec'])
  })

  it('adds the coverage of a partial month to its label', () => {
    expect(createPanel(FIXTURE, '2025-06').find('.panel-label').text()).toBe(
      'June 2025 · from 27 Jun'
    )
  })

  it('colours the euros and the percentage each by its own sign', () => {
    const wrapper = createPanel(
      [row('2025-01-31'), row('2025-02-10', -100, 900), row('2025-02-28', 60, 2217.14)],
      '2025-02'
    )
    expect([
      wrapper.find('.month-amount').text(),
      wrapper.find('.month-amount').classes('text-gain'),
      wrapper.find('.month-percent').text(),
      wrapper.find('.month-percent').classes('text-loss'),
    ]).toEqual(['+€60.00', true, '−3.0%', true])
  })

  it('prints a dash and says why when the percentage is unavailable', () => {
    const wrapper = createPanel([row('2025-01-31'), row('2025-02-28', 10, 10)], '2025-02')
    expect([wrapper.find('.month-percent').text(), textsOf(wrapper, '.month-note')]).toEqual([
      '—',
      ['No daily summary for 1 to 27 Feb', 'Percentage unavailable'],
    ])
  })

  it('puts every note on its own line', () => {
    const wrapper = createPanel(
      [row('2021-06-30'), row('2021-07-31', 1, 1001), row('2021-09-28', 2, 1)],
      '2021-09'
    )
    expect(textsOf(wrapper, '.month-note')).toEqual([
      'Covers 2 months, no daily summary for Aug 2021',
      'No daily summary for 1 to 27 Sep',
      'Percentage unavailable',
    ])
  })

  it('steps to the neighbouring months and disables an arrow at an end', async () => {
    const wrapper = createPanel(FIXTURE, '2025-12', { next: null })
    const [previous, next] = ['Previous month', 'Next month'].map(label =>
      wrapper.find(`button[aria-label="${label}"]`)
    )

    await previous.trigger('click')
    await next.trigger('click')

    expect([wrapper.emitted('select'), next.attributes('aria-disabled')]).toEqual([[['p']], 'true'])
  })
})
