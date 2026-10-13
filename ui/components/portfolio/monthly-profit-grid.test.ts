import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import DailyProfitLegend from './daily-profit-legend.vue'
import MonthlyProfitGrid from './monthly-profit-grid.vue'
import { buildMonthlyProfit, type MonthlyProfit } from '../../services/monthly-profit'
import { MONTH_STEPS } from '../../services/monthly-profit-texts'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

enableAutoUnmount(afterEach)

const LATER = '2030-01-15'

const row = (date: string, totalProfit = 0, totalValue = 1000) =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue })

const fixture = () =>
  buildMonthlyProfit(mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]), '2026-01-15')!

const sparse = () =>
  buildMonthlyProfit(
    [
      row('2019-12-31'),
      row('2020-01-31', 10, 1010),
      row('2021-07-31', 20, 1020),
      row('2021-09-30', 30, 1030),
      row('2022-01-31', 40, 1040),
    ],
    LATER
  )!

const adjacent = () =>
  buildMonthlyProfit(
    [
      row('2024-10-31'),
      row('2024-11-30', -100, 900),
      row('2024-12-31', -90, 910),
      row('2025-01-31', 0, 1000),
      row('2025-02-28', 0, 1000),
    ],
    LATER
  )!

const createGrid = (monthly: MonthlyProfit, selected: string, previewed?: string) =>
  mount(MonthlyProfitGrid, { props: { monthly, selected, previewed }, attachTo: document.body })

type Grid = ReturnType<typeof createGrid>

const press = (wrapper: Grid, key: string, init: KeyboardEventInit = {}) =>
  wrapper
    .find('[role=application]')
    .element.dispatchEvent(
      new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init })
    )

const cell = (wrapper: Grid, name: string) => wrapper.find(`[aria-label^="${name}"]`)

describe('MonthlyProfitGrid', () => {
  it('draws one row of twelve slots and a year total per year, newest first', () => {
    const wrapper = createGrid(sparse(), '2022-01')
    expect([
      wrapper.findAll('.year-label').map(label => label.text()),
      wrapper.findAll('.month-slot').length,
      wrapper.findAll('.year-total').length,
      wrapper.findAll('.month-label').map(label => label.text()),
    ]).toEqual([
      ['2022', '2021', '2020'],
      36,
      3,
      ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Year'],
    ])
  })

  it('prints each month and the year of the fixture with its sign and no percent sign in the cells', () => {
    const wrapper = createGrid(fixture(), '2025-12')
    expect([
      wrapper.findAll('.month-cell').map(month => month.text()),
      wrapper.find('.year-total').text(),
      wrapper.find('.year-total').attributes('aria-label'),
      wrapper.findAll('.year-note').map(note => note.text()),
    ]).toEqual([
      ['+0.4', '+3.8', '+8.0', '+4.0', '+2.9', '+8.3', '+5.4'],
      '+37.6%',
      '2025 from 27 Jun, +€44,343.78, +37.6%',
      ['2025 from 27 Jun'],
    ])
  })

  it('prints a dash for a year without a result and explains it below', () => {
    const wrapper = createGrid(sparse(), '2022-01')
    expect([
      wrapper.findAll('.year-total').map(total => total.text()),
      wrapper.findAll('.year-note').map(note => note.text()),
    ]).toEqual([
      ['—', '—', '+1.0%'],
      [
        'Year result unavailable: missing year-end summary',
        'Year result unavailable: missing year-end summary',
        '2020 to 31 Jan',
      ],
    ])
  })

  it('draws a missing month dashed, named, empty and not selectable', async () => {
    const wrapper = createGrid(sparse(), '2021-09')
    const missing = cell(wrapper, 'August 2021')

    await missing.trigger('click')
    await missing.trigger('pointerenter', { pointerType: 'mouse' })

    expect([
      missing.attributes('aria-label'),
      missing.text(),
      missing.classes('border-dashed'),
      missing.classes('selectable'),
      wrapper.emitted('select'),
      wrapper.emitted('preview'),
    ]).toEqual(['August 2021, no daily summary', '', true, false, undefined, undefined])
  })

  it('sets surface text on the darkest step and ink on the others', () => {
    const wrapper = createGrid(adjacent(), '2025-02')
    const colours = wrapper
      .findAll('.month-cell')
      .map(month => month.classes().filter(name => /^(bg|text)-/.test(name)))
    expect(colours).toEqual([
      ['bg-gain', 'text-surface'],
      ['bg-change-flat', 'text-ink'],
      ['bg-loss', 'text-surface'],
      ['bg-gain-step-2', 'text-ink'],
    ])
  })

  it('moves a month sideways across the year boundary', () => {
    const later = createGrid(adjacent(), '2024-12')
    const earlier = createGrid(adjacent(), '2025-01')

    press(later, 'ArrowRight')
    press(earlier, 'ArrowLeft')

    expect([later.emitted('select'), earlier.emitted('select')]).toEqual([
      [['2025-01']],
      [['2024-12']],
    ])
  })

  it('moves a year up or down and skips a missing month in steps of a year', () => {
    const up = createGrid(sparse(), '2020-01')
    const down = createGrid(sparse(), '2022-01')

    press(up, 'ArrowUp')
    press(down, 'ArrowDown')

    expect([up.emitted('select'), down.emitted('select')]).toEqual([[['2022-01']], [['2020-01']]])
  })

  it('emits nothing at the ends, with a modifier key or for another key', () => {
    const wrapper = createGrid(sparse(), '2022-01')

    press(wrapper, 'ArrowRight')
    press(wrapper, 'ArrowUp')
    press(wrapper, 'ArrowLeft', { metaKey: true })
    press(wrapper, 'Home')

    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('selects a clicked month and takes the focus', async () => {
    const wrapper = createGrid(sparse(), '2022-01')

    await cell(wrapper, 'July 2021').trigger('click')

    expect([wrapper.emitted('select'), document.activeElement?.getAttribute('role')]).toEqual([
      [['2021-07']],
      'application',
    ])
  })

  it('previews a hovered month, ignores touch and clears on leaving', async () => {
    const wrapper = createGrid(sparse(), '2022-01')
    const july = cell(wrapper, 'July 2021')

    await july.trigger('pointerenter', { pointerType: 'touch' })
    await july.trigger('pointerenter', { pointerType: 'mouse' })
    await wrapper.find('[role=application]').trigger('pointerleave')

    expect(wrapper.emitted('preview')).toEqual([['2021-07'], [null]])
  })

  it('is one named tab stop described by its selected month', () => {
    const wrapper = createGrid(sparse(), '2021-07', '2021-09')
    const grid = wrapper.find('[role=application]')
    const selected = cell(wrapper, 'July 2021')
    expect([
      grid.attributes('aria-label'),
      grid.attributes('tabindex'),
      grid.attributes('aria-describedby') === selected.attributes('id'),
      selected.attributes('aria-current'),
      selected.attributes('title'),
      wrapper.findAll('[aria-current]').length,
      cell(wrapper, 'September 2021').classes('previewed'),
      wrapper.findAll('[tabindex]').length,
    ]).toEqual([
      'Monthly profit calendar',
      '0',
      true,
      'true',
      'July 2021, 1 Feb – 31 Jul, +€10.00, +1.0%',
      1,
      true,
      1,
    ])
  })
})

describe('DailyProfitLegend steps', () => {
  const ticks = (props: { missing: boolean; steps?: number[] }) =>
    mount(DailyProfitLegend, { props })
      .findAll('.legend-tick')
      .map(tick => tick.text())

  it('prints the monthly thresholds when it is given them', () => {
    expect(ticks({ missing: false, steps: MONTH_STEPS })).toEqual([
      '5',
      '2.5',
      '1',
      '±0.3%',
      '1',
      '2.5',
      '5',
    ])
  })

  it('prints the daily thresholds without the prop', () => {
    expect(ticks({ missing: false })).toEqual([
      '1.5',
      '0.75',
      '0.3',
      '±0.05%',
      '0.3',
      '0.75',
      '1.5',
    ])
  })
})
