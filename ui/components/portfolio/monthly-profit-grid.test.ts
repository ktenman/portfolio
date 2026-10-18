import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import {
  createCalendarRow as row,
  createMissingMonthRows,
  createMonthlyProfit,
} from '../../tests/fixtures'
import type { MonthlyProfit } from '../../services/monthly-profit'
import { monthlyYears } from '../../services/monthly-profit-years'
import { addMonths, lastDayOfMonth } from '../../utils/iso-dates'
import MonthlyProfitGrid from './monthly-profit-grid.vue'

enableAutoUnmount(afterEach)

const monthEnds = (first: string, count: number, skipped: string) =>
  createMonthlyProfit(
    Array.from({ length: count }, (_, index) => addMonths(first, index))
      .filter(key => key !== skipped)
      .map((key, index) => row(lastDayOfMonth(key), index * 100)),
    '2030-01-01'
  )

const mountGrid = (model: MonthlyProfit = createMonthlyProfit(), selected = '2025-12') =>
  mount(MonthlyProfitGrid, {
    props: { model, years: monthlyYears(model), selected, previewed: null },
    attachTo: document.body,
  })

type Wrapper = ReturnType<typeof mountGrid>

const gridOf = (wrapper: Wrapper) => wrapper.find('[role="application"]')
const cellNamed = (wrapper: Wrapper, name: string) => wrapper.find(`[aria-label^="${name}"]`)

const pressKey = (wrapper: Wrapper, init: KeyboardEventInit) => {
  const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init })
  gridOf(wrapper).element.dispatchEvent(event)
  return event.defaultPrevented
}

const selections = (wrapper: Wrapper) => wrapper.emitted('select')?.map(([key]) => key)

describe('MonthlyProfitGrid', () => {
  it('renders one row per year with month percentages and the year total', () => {
    const wrapper = mountGrid()
    const total = wrapper.find('.year-total')

    expect([
      wrapper.findAll('.month-cell').map(cell => cell.text()),
      total.text(),
      total.attributes('aria-label'),
    ]).toEqual([
      ['0.4', '3.8', '8.0', '4.0', '2.9', '8.3', '5.4'],
      '37.6%',
      '2025, from 27 Jun, +€44,343.78, +37.6%',
    ])
  })

  it('paints each step with its fill and text colour', () => {
    const wrapper = mountGrid()
    const colours = ['June', 'July', 'August'].map(month =>
      cellNamed(wrapper, `${month} 2025`)
        .classes()
        .filter(name => name.startsWith('bg-') || name.startsWith('text-'))
    )

    expect(colours).toEqual([
      ['bg-gain-step-1', 'text-ink'],
      ['bg-gain-step-3', 'text-ink'],
      ['bg-gain', 'text-surface'],
    ])
  })

  it('names the calendar and describes the selected month', () => {
    const wrapper = mountGrid()
    const grid = gridOf(wrapper)
    const december = cellNamed(wrapper, 'December 2025')

    expect([
      grid.attributes('aria-label'),
      grid.attributes('tabindex'),
      grid.attributes('aria-describedby'),
      december.attributes('aria-current'),
    ]).toEqual(['Monthly profit calendar', '0', december.attributes('id'), 'true'])
  })

  it('draws a missing month as a named dashed slot that cannot be selected', async () => {
    const model = createMonthlyProfit(createMissingMonthRows(), '2030-01-01')
    const wrapper = mountGrid(model, '2021-07')
    const august = wrapper.find('[aria-label="August 2021, no daily summary"]')

    await august.trigger('click')

    expect([august.classes('border-dashed'), selections(wrapper)]).toEqual([true, undefined])
  })

  it('moves one month with Left and Right across a year boundary', async () => {
    const wrapper = mountGrid(monthEnds('2023-02', 26, '2024-03'), '2025-01')

    pressKey(wrapper, { key: 'ArrowLeft' })
    await wrapper.setProps({ selected: '2024-12' })
    pressKey(wrapper, { key: 'ArrowRight' })

    expect(selections(wrapper)).toEqual(['2024-12', '2025-01'])
  })

  it('moves twelve months with Up and Down and skips the missing month', async () => {
    const wrapper = mountGrid(monthEnds('2023-02', 26, '2024-03'), '2023-03')

    pressKey(wrapper, { key: 'ArrowUp' })
    await wrapper.setProps({ selected: '2025-03' })
    pressKey(wrapper, { key: 'ArrowDown' })

    expect(selections(wrapper)).toEqual(['2025-03', '2023-03'])
  })

  it('stays put at the ends', () => {
    const last = mountGrid()
    pressKey(last, { key: 'ArrowRight' })
    pressKey(last, { key: 'ArrowUp' })
    const first = mountGrid(createMonthlyProfit(), '2025-06')

    pressKey(first, { key: 'ArrowLeft' })

    expect([selections(last), selections(first)]).toEqual([undefined, undefined])
  })

  it('ignores keys with modifiers', () => {
    const wrapper = mountGrid()

    const prevented = pressKey(wrapper, { key: 'ArrowLeft', ctrlKey: true })

    expect([prevented, selections(wrapper)]).toEqual([false, undefined])
  })

  it('selects on click and focuses the grid', async () => {
    const wrapper = mountGrid()

    await cellNamed(wrapper, 'November 2025').trigger('click')

    expect([selections(wrapper), document.activeElement]).toEqual([
      ['2025-11'],
      gridOf(wrapper).element,
    ])
  })

  it('previews with a mouse but not with touch', async () => {
    const wrapper = mountGrid()
    const november = cellNamed(wrapper, 'November 2025')

    await november.trigger('pointerenter', { pointerType: 'mouse' })
    await november.trigger('pointerenter', { pointerType: 'touch' })
    await gridOf(wrapper).trigger('pointerleave')

    expect(wrapper.emitted('preview')).toEqual([['2025-11'], [null]])
  })

  it('lists the year coverage below the grid', () =>
    expect(mountGrid().find('[role="application"] + .year-notes').text()).toBe(
      '2025 · from 27 Jun'
    ))
})
