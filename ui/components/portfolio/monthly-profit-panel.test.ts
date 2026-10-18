import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { createCalendarRow as row, createMonthlyProfit } from '../../tests/fixtures'
import { monthOn, type ObservedMonth } from '../../services/monthly-profit'
import MonthlyProfitPanel from './monthly-profit-panel.vue'

const fixtureMonth = (key: string) => monthOn(createMonthlyProfit(), key)!

const february = (rows: PortfolioSummaryDto[]) =>
  monthOn(createMonthlyProfit(rows, '2030-01-01'), '2025-02')!

const mountPanel = (
  month: ObservedMonth,
  previous: string | null = null,
  next: string | null = null
) => mount(MonthlyProfitPanel, { props: { month, previous, next } })

type Wrapper = ReturnType<typeof mountPanel>

const textsOf = (wrapper: Wrapper, selectors: string[]) =>
  selectors.map(selector => wrapper.find(selector).text().replace(/\s+/g, ' '))

const arrow = (wrapper: Wrapper, label: string) => wrapper.find(`button[aria-label="${label}"]`)

describe('MonthlyProfitPanel', () => {
  it("shows December's figures", () => {
    const wrapper = mountPanel(fixtureMonth('2025-12'), '2025-11')

    expect(
      textsOf(wrapper, [
        '.month-label',
        '.month-amount',
        '.month-percent',
        '.month-detail',
        '.month-note',
      ])
    ).toEqual([
      'December 2025',
      '+€9,110.23',
      '+5.4%',
      '3rd best of 7 months · closed at €178,204.06 · XIRR 21.18%',
      'No daily summary for 30 Dec',
    ])
  })

  it('adds the coverage to the label', () =>
    expect(textsOf(mountPanel(fixtureMonth('2025-06')), ['.month-label'])).toEqual([
      'June 2025 · from 27 Jun',
    ]))

  it('colours the amount and the percentage independently', () => {
    const wrapper = mountPanel(
      february([
        row('2025-01-31', 0, 1000),
        row('2025-02-01', -40, 960),
        row('2025-02-02', 60, 10100),
      ])
    )

    expect([
      wrapper.find('.month-amount').classes('text-gain'),
      wrapper.find('.month-percent').classes('text-loss'),
    ]).toEqual([true, true])
  })

  it('shows a dash and the note when the percentage is unavailable', () => {
    const wrapper = mountPanel(february([row('2025-01-31', 0), row('2025-02-01', 100, 100)]))

    expect([
      ...textsOf(wrapper, ['.month-percent', '.month-note']),
      wrapper.find('.month-percent').classes(),
    ]).toEqual(['—', 'Percentage unavailable', ['month-percent']])
  })

  it('disables the previous arrow at the first month and emits neighbours', async () => {
    const wrapper = mountPanel(fixtureMonth('2025-06'), null, '2025-07')

    await arrow(wrapper, 'Previous month').trigger('click')
    await arrow(wrapper, 'Next month').trigger('click')

    expect([
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      wrapper.emitted('select'),
    ]).toEqual(['true', [['2025-07']]])
  })
})
