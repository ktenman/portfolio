import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createCalendarRow as row } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'
import { buildMonthlyProfit, type MonthlyProfit } from '../../services/monthly-profit'
import { monthlyYears } from '../../services/monthly-profit-years'
import { mergeHistoricalWithCurrent } from '../../services/summary-aggregator'
import DailyProfitCell from './daily-profit-cell.vue'
import MonthlyProfitCompact from './monthly-profit-compact.vue'

const fixtureModel = () =>
  buildMonthlyProfit(mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY), '2026-01-15')!

const mountCompact = (model: MonthlyProfit = fixtureModel()) =>
  mount(MonthlyProfitCompact, { props: { model, years: monthlyYears(model) } })

const variantsOf = (wrapper: ReturnType<typeof mountCompact>) =>
  wrapper.findAllComponents(DailyProfitCell).map(cell => cell.props('variant'))

describe('MonthlyProfitCompact', () => {
  it('summarises the years and figures', () => {
    const wrapper = mountCompact()

    expect([
      wrapper.find('h2').text(),
      wrapper.find('.compact-counts').text(),
      wrapper.find('.compact-span').text(),
      wrapper.find('.dot-total').text(),
      wrapper.find('.sr-only').text(),
    ]).toEqual([
      'Monthly profit',
      '7 up · 0 down',
      'Jun – Dec 2025',
      '+37.6%',
      '2025, from 27 Jun, +€44,343.78, +37.6%',
    ])
  })

  it('paints a dot per month in its step and keeps empty slots in their columns', () => {
    const wrapper = mountCompact()

    expect([variantsOf(wrapper), wrapper.find('.dot-grid').element.children.length]).toEqual([
      ['gain-1', 'gain-3', 'gain-4', 'gain-3', 'gain-3', 'gain-4', 'gain-4'],
      28,
    ])
  })

  it('draws a missing month as a dashed dot', () =>
    expect(
      variantsOf(
        mountCompact(
          buildMonthlyProfit(
            [row('2021-06-30', 0), row('2021-07-31', 100), row('2021-09-30', 200)],
            '2030-01-01'
          )!
        )
      )
    ).toEqual(['flat', 'missing', 'flat']))

  it('hides the dots from assistive technology and ignores taps', () => {
    const wrapper = mountCompact()
    const dots = wrapper.findAllComponents(DailyProfitCell)

    expect([
      wrapper.find('.dot-grid').attributes('aria-hidden'),
      dots.some(dot => dot.attributes('tabindex') !== undefined),
      dots.some(dot => dot.attributes('role') === 'button'),
    ]).toEqual(['true', false, false])
  })
})
