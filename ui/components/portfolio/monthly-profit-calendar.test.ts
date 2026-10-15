import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import MonthlyProfitCalendar from './monthly-profit-calendar.vue'
import { buildMonthlyProfit, type MonthlyProfit } from '../../services/monthly-profit'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

enableAutoUnmount(afterEach)

const fixture = () =>
  buildMonthlyProfit(mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]), '2026-01-15')!

const createCard = (monthly: MonthlyProfit | null | undefined = fixture()) =>
  mount(MonthlyProfitCalendar, { props: { monthly }, attachTo: document.body })

type Card = ReturnType<typeof createCard>

const cell = (wrapper: Card, name: string) => wrapper.find(`.month-grid [aria-label^="${name}"]`)
const label = (wrapper: Card) => wrapper.find('.panel-label').text()
const arrow = (wrapper: Card, name: string) => wrapper.find(`button[aria-label="${name}"]`)
const status = (wrapper: Card) => wrapper.find('[role=status]').text()

describe('MonthlyProfitCalendar', () => {
  it('opens on the latest month under its heading and span, announcing nothing', () => {
    const wrapper = createCard()
    expect([
      wrapper.find('h2').text(),
      wrapper.find('.calendar-span').text(),
      label(wrapper),
      arrow(wrapper, 'Next month').attributes('aria-disabled'),
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      cell(wrapper, 'December 2025').attributes('aria-current'),
      status(wrapper),
    ]).toEqual(['Monthly profit', 'Jun – Dec 2025', 'December 2025', 'true', undefined, 'true', ''])
  })

  it('shows and announces a clicked month and stops the arrow at the first month', async () => {
    const wrapper = createCard()

    await cell(wrapper, 'June 2025').trigger('click')

    expect([
      label(wrapper),
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      status(wrapper).startsWith('June 2025, from 27 Jun, +€455.37, +0.4%'),
    ]).toEqual(['June 2025 · from 27 Jun', 'true', true])
  })

  it('steps a month with the panel arrow', async () => {
    const wrapper = createCard()
    await arrow(wrapper, 'Previous month').trigger('click')
    expect(label(wrapper)).toBe('November 2025')
  })

  it('previews a hovered month without announcing it and restores the selection on leaving', async () => {
    const wrapper = createCard()

    await cell(wrapper, 'July 2025').trigger('pointerenter', { pointerType: 'mouse' })
    const previewed = [label(wrapper), status(wrapper)]
    await wrapper.find('.month-grid').trigger('pointerleave')

    expect([previewed, label(wrapper)]).toEqual([['July 2025', ''], 'December 2025'])
  })

  it('selects the best month from its shortcut', async () => {
    const wrapper = createCard()
    await wrapper.find('.extreme-btn').trigger('click')
    expect(label(wrapper)).toBe('November 2025')
  })

  it('prints the monthly thresholds in its legend and the missing key only with a missing month', () => {
    const sparse = buildMonthlyProfit(
      [
        createPortfolioSummaryDto({ date: '2021-06-30' }),
        createPortfolioSummaryDto({ date: '2021-07-31' }),
        createPortfolioSummaryDto({ date: '2021-09-30' }),
      ],
      '2030-01-15'
    )
    expect([
      createCard()
        .findAll('.legend-tick')
        .map(tick => tick.text()),
      createCard().find('.legend-missing').exists(),
      createCard(sparse).find('.legend-missing').exists(),
    ]).toEqual([['5', '2.5', '1', '±0.3%', '1', '2.5', '5'], false, true])
  })

  it('is a hidden skeleton until the model exists', () => {
    const wrapper = mount(MonthlyProfitCalendar, { props: { monthly: undefined } })
    expect([wrapper.attributes('aria-hidden'), wrapper.find('.skeleton').exists()]).toEqual([
      'true',
      true,
    ])
  })

  it('says so when the history is too short for a month', () => {
    expect(createCard(null).text()).toBe(
      'Not enough daily summary history to calculate monthly changes.'
    )
  })
})
