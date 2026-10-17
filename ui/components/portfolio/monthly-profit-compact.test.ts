import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMissingMonthRows, createMonthlyProfit } from '../../tests/fixtures'
import type { MonthlyProfit } from '../../services/monthly-profit'
import { monthlyYears } from '../../services/monthly-profit-years'
import DailyProfitCell from './daily-profit-cell.vue'
import MonthlyProfitCompact from './monthly-profit-compact.vue'

const mountCompact = (model: MonthlyProfit = createMonthlyProfit()) =>
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
      '37.6%',
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
      variantsOf(mountCompact(createMonthlyProfit(createMissingMonthRows(), '2030-01-01')))
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
