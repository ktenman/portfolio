import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { NOT_ENOUGH_HISTORY, type MonthlyProfit } from '../../services/monthly-profit'
import { mergeHistoricalWithCurrent } from '../../services/summary-aggregator'
import { createMissingMonthRows, createMonthlyProfit } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'
import MonthlyProfitCalendar from './monthly-profit-calendar.vue'

enableAutoUnmount(afterEach)

const ROWS = mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY)
const NOVEMBER = 'November 2025, +€12,307.39, +8.3%, Best of 7 months, ranked by euro profit'

const mountCalendar = (
  model: MonthlyProfit | null | undefined,
  error: Error | null = null,
  refreshing = false
) => mount(MonthlyProfitCalendar, { props: { model, error, refreshing }, attachTo: document.body })

type Wrapper = ReturnType<typeof mountCalendar>

const cell = (wrapper: Wrapper, title: string) =>
  wrapper.find(`.calendar-grid [aria-label^="${title},"]`)
const arrow = (wrapper: Wrapper, label: string) => wrapper.find(`button[aria-label="${label}"]`)
const panelLabel = (wrapper: Wrapper) => wrapper.find('.month-label').text()
const announced = (wrapper: Wrapper) => wrapper.find('[role="status"]').text()

describe('MonthlyProfitCalendar', () => {
  it('shows placeholders until the months arrive', () => {
    const wrapper = mountCalendar(undefined)

    expect([wrapper.attributes('aria-hidden'), wrapper.find('.calendar-grid').exists()]).toEqual([
      'true',
      false,
    ])
  })

  it('explains when there is not enough history', () => {
    const wrapper = mountCalendar(null)

    expect(wrapper.text()).toBe(NOT_ENOUGH_HISTORY)
  })

  it('leaves announcing too little history to the interval switch', () =>
    expect(mountCalendar(null).find('[role="alert"]').exists()).toBe(false))

  it('shows the fixture with December selected', () => {
    const wrapper = mountCalendar(createMonthlyProfit())

    expect([
      wrapper.find('.calendar-heading').text(),
      wrapper.find('.calendar-span').text(),
      panelLabel(wrapper),
      arrow(wrapper, 'Next month').attributes('aria-disabled'),
      arrow(wrapper, 'Previous month').attributes('aria-disabled'),
      wrapper.findAll('.extreme-btn').map(button => button.text().split(' ')[0]),
    ]).toEqual(['Monthly profit', 'Jun – Dec 2025', 'December 2025', 'true', undefined, ['Best']])
  })

  it('lists the year coverage at the end of the legend', () =>
    expect(
      mountCalendar(createMonthlyProfit()).find('.calendar-legend > .year-notes:last-child').text()
    ).toBe('2025 from 27 Jun'))

  it('disables the previous arrow on the first month', async () => {
    const wrapper = mountCalendar(createMonthlyProfit())

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
    const wrapper = mountCalendar(createMonthlyProfit())

    await cell(wrapper, 'July 2025').trigger('pointerenter', { pointerType: 'mouse' })
    const hovered = [panelLabel(wrapper), announced(wrapper)]
    await wrapper.find('.calendar-grid').trigger('pointerleave')

    expect([...hovered, panelLabel(wrapper)]).toEqual(['July 2025', '', 'December 2025'])
  })

  it('jumps to the best month from its shortcut', async () => {
    const wrapper = mountCalendar(createMonthlyProfit())

    await wrapper.find('.extreme-btn').trigger('click')

    expect([panelLabel(wrapper), announced(wrapper)]).toEqual(['November 2025', NOVEMBER])
  })

  it('keeps the selection when a background refresh rebuilds the months', async () => {
    const wrapper = mountCalendar(createMonthlyProfit())
    const refreshed = ROWS.map(entry =>
      entry.date === TODAY_SUMMARY.date ? { ...entry, totalProfit: entry.totalProfit + 50 } : entry
    )

    await cell(wrapper, 'November 2025').trigger('click')
    await wrapper.setProps({ model: createMonthlyProfit(refreshed) })

    expect([panelLabel(wrapper), announced(wrapper)]).toEqual(['November 2025', NOVEMBER])
  })

  it('offers a retry when a refresh fails', async () => {
    const wrapper = mountCalendar(createMonthlyProfit(), new Error('down'))

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

  it('labels the retry as running while the history refreshes', () =>
    expect(
      mountCalendar(createMonthlyProfit(), new Error('down'), true)
        .find('.alert-warning button')
        .text()
    ).toBe('Retrying…'))

  it('moves focus to the card when a retry succeeds', async () => {
    const wrapper = mountCalendar(createMonthlyProfit(), new Error('down'))
    wrapper.find<HTMLButtonElement>('.alert-warning button').element.focus()

    await wrapper.setProps({ error: null })

    expect(document.activeElement).toBe(wrapper.element)
  })

  it('keeps focus on the grid when a background refresh recovers', async () => {
    const wrapper = mountCalendar(createMonthlyProfit(), new Error('down'))
    const grid = wrapper.find<HTMLElement>('[role="application"]').element
    grid.focus()

    await wrapper.setProps({ error: null })

    expect(document.activeElement).toBe(grid)
  })

  it("shows the legend's missing key only when a month is missing", () => {
    const gap = createMonthlyProfit(createMissingMonthRows(), '2030-01-01')

    expect(
      [createMonthlyProfit(), gap].map(model =>
        mountCalendar(model).find('.legend-missing').exists()
      )
    ).toEqual([false, true])
  })
})
