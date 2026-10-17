import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import type { HistoryStatus } from '../../composables/use-summary-calendar-query'
import {
  buildMonthlyProfit,
  NOT_ENOUGH_HISTORY,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { mergeHistoricalWithCurrent } from '../../services/summary-aggregator'
import { createCalendarRow as row } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'
import MonthlyProfitCalendar from './monthly-profit-calendar.vue'

enableAutoUnmount(afterEach)

const ROWS = mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY)
const NOVEMBER = 'November 2025, +€12,307.39, +8.3%, Best of 7 months, ranked by euro profit'

const build = (rows: PortfolioSummaryDto[] = ROWS) => buildMonthlyProfit(rows, '2026-01-15')!

const mountCalendar = (
  model: MonthlyProfit | null | undefined,
  status: HistoryStatus = 'success'
) => mount(MonthlyProfitCalendar, { props: { model, status }, attachTo: document.body })

type Wrapper = ReturnType<typeof mountCalendar>

const cell = (wrapper: Wrapper, title: string) =>
  wrapper.find(`.calendar-grid [aria-label^="${title},"]`)
const arrow = (wrapper: Wrapper, label: string) => wrapper.find(`button[aria-label="${label}"]`)
const panelLabel = (wrapper: Wrapper) => wrapper.find('.month-label').text()
const announced = (wrapper: Wrapper) => wrapper.find('[role="status"]').text()

describe('MonthlyProfitCalendar', () => {
  it('shows placeholders until the months arrive', () => {
    const wrapper = mountCalendar(undefined, 'loading')

    expect([wrapper.attributes('aria-hidden'), wrapper.find('.calendar-grid').exists()]).toEqual([
      'true',
      false,
    ])
  })

  it('explains when there is not enough history', () => {
    const wrapper = mountCalendar(null)

    expect(wrapper.text()).toBe(NOT_ENOUGH_HISTORY)
  })

  it('shows the fixture with December selected', () => {
    const wrapper = mountCalendar(build())

    expect([
      wrapper.find('.calendar-heading').text(),
      wrapper.find('.calendar-span').text(),
      panelLabel(wrapper),
      arrow(wrapper, 'Next month').attributes('aria-disabled'),
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      wrapper.findAll('.extreme-btn').map(button => button.text().split(' ')[0]),
    ]).toEqual(['Monthly profit', 'Jun – Dec 2025', 'December 2025', 'true', undefined, ['Best']])
  })

  it('disables the previous arrow on the first month', async () => {
    const wrapper = mountCalendar(build())

    await cell(wrapper, 'June 2025').trigger('click')

    expect([
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      announced(wrapper),
    ]).toEqual([
      'true',
      'June 2025, from 27 Jun, +€455.37, +0.4%, 7th best of 7 months, ranked by euro profit',
    ])
  })

  it('previews on hover without announcing and restores on leave', async () => {
    const wrapper = mountCalendar(build())

    await cell(wrapper, 'July 2025').trigger('pointerenter', { pointerType: 'mouse' })
    const hovered = [panelLabel(wrapper), announced(wrapper)]
    await wrapper.find('.calendar-grid').trigger('pointerleave')

    expect([...hovered, panelLabel(wrapper)]).toEqual(['July 2025', '', 'December 2025'])
  })

  it('jumps to the best month from its shortcut', async () => {
    const wrapper = mountCalendar(build())

    await wrapper.find('.extreme-btn').trigger('click')

    expect([panelLabel(wrapper), announced(wrapper)]).toEqual(['November 2025', NOVEMBER])
  })

  it('keeps the selection when a background refresh rebuilds the months', async () => {
    const wrapper = mountCalendar(build())
    const refreshed = ROWS.map(entry =>
      entry.date === TODAY_SUMMARY.date ? { ...entry, totalProfit: entry.totalProfit + 50 } : entry
    )

    await cell(wrapper, 'November 2025').trigger('click')
    await wrapper.setProps({ model: build(refreshed) })

    expect([panelLabel(wrapper), announced(wrapper)]).toEqual(['November 2025', NOVEMBER])
  })

  it('offers a retry when a refresh fails', async () => {
    const wrapper = mountCalendar(build(), 'error')

    await wrapper.find('.alert-warning button').trigger('click')

    expect([
      wrapper.find('.alert-warning span').text(),
      wrapper.emitted('retry'),
      wrapper.find('.calendar-grid').exists(),
    ]).toEqual([
      'Could not refresh the monthly history. Showing data as of Wed 31 Dec 2025.',
      [[]],
      true,
    ])
  })

  it("shows the legend's missing key only when a month is missing", () => {
    const gap = buildMonthlyProfit(
      [row('2021-06-30', 0), row('2021-07-31', 100), row('2021-09-30', 200)],
      '2030-01-01'
    )!

    expect(
      [build(), gap].map(model => mountCalendar(model).find('.legend-missing').exists())
    ).toEqual([false, true])
  })
})
