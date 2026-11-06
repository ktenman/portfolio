import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DailyProfitCell from './daily-profit-cell.vue'
import DailyProfitCompact from './daily-profit-compact.vue'
import DailyProfitGrid from './daily-profit-grid.vue'
import DailyProfitPanel from './daily-profit-panel.vue'
import {
  createCalendarRow,
  createCalendarRows,
  createDailyProfitCalendar,
} from '../../tests/fixtures'

const propsFor = (rows = createCalendarRows(), today?: string) => {
  const calendar = createDailyProfitCalendar(rows, today)
  return { calendar, day: calendar.days[calendar.days.length - 1] }
}

const createWrapper = (...args: Parameters<typeof propsFor>) =>
  mount(DailyProfitCompact, { props: propsFor(...args) })

const rowsOf = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper
    .findAll('.compact-row')
    .map(row =>
      row.findAll('.compact-label, .compact-sub, .compact-value').map(text => text.text())
    )

describe('DailyProfitCompact', () => {
  it('should head the module with its name and the two counts', () => {
    const wrapper = createWrapper()

    expect([wrapper.find('h2').text(), wrapper.find('.compact-counts').text()]).toEqual([
      'Daily profit',
      '4 up · 2 down',
    ])
  })

  it('should put its slot between the heading and the grid', () => {
    const wrapper = mount(DailyProfitCompact, {
      props: propsFor(),
      slots: { default: '<b class="slotted">period</b>' },
    })

    expect([
      Array.from(wrapper.find('.compact-head').element.children).map(child => child.classList[0]),
      wrapper.find('.compact-period .slotted').exists(),
    ]).toEqual([['compact-title', 'compact-period', 'calendar-grid'], true])
  })

  it('should pass busy on to its grid', () => {
    const wrapper = mount(DailyProfitCompact, {
      props: { ...propsFor(), busy: true },
    })

    expect(wrapper.findComponent(DailyProfitGrid).props('busy')).toBe(true)
  })

  it('should draw the grid in compact mode on the selected day', () => {
    const grid = createWrapper().findComponent(DailyProfitGrid)

    expect([grid.props('compact'), grid.props('selected')]).toEqual([true, '2025-12-09'])
  })

  it('should show the selected day in a day card under the grid', () => {
    const wrapper = createWrapper()
    const panel = wrapper.findComponent(DailyProfitPanel)

    expect([
      wrapper.find('.compact-day .day-label').text(),
      panel.props('previous'),
      panel.props('next'),
    ]).toEqual(['Tue 9 Dec 2025', '2025-12-08', null])
  })

  it('should leave the gap note out of the day card', () => {
    const props = propsFor([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-03', 1300),
    ])
    const wrapper = mount(DailyProfitCompact, { props })

    expect([props.day.note, wrapper.find('.compact-day .day-note').exists()]).toEqual([
      'Covers 2 days, no daily summary for 2 Dec',
      false,
    ])
  })

  it('should pass a tap on the grid and the day buttons on as a selection', async () => {
    const wrapper = createWrapper()

    wrapper.findComponent(DailyProfitGrid).vm.$emit('select', '2025-12-07')
    await wrapper.find('button[aria-label="Previous day"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12-07'], ['2025-12-08']])
  })

  it('should list the flat days, the average day, the best day and the worst day', () => {
    expect(rowsOf(createWrapper())).toEqual([
      ['Flat days', 'under ±0.05%', '1'],
      ['Average day', '+€225.71'],
      ['Best day · Tue 9 Dec', '+€700.00'],
      ['Worst day · Wed 3 Dec', '−€600.00'],
    ])
  })

  it('should make the best and the worst day buttons that select their day', async () => {
    const wrapper = createWrapper()
    const [best, worst] = wrapper.findAll('button.compact-row')

    await best.trigger('click')
    await worst.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12-09'], ['2025-12-03']])
  })

  it('should leave the swatch off the best and the worst day buttons', () => {
    const swatches = createWrapper()
      .findAll('button.compact-row')
      .map(button => button.findComponent(DailyProfitCell).exists())

    expect(swatches).toEqual([false, false])
  })

  it('should print the cents of the average, the best and the worst amount on their own so they can be set smaller', () => {
    const cents = createWrapper().findAll('.compact-cents')

    expect(cents.map(text => text.text())).toEqual(['.71', '.00', '.00'])
  })

  it('should print gains and losses in their colours', () => {
    const wrapper = createWrapper()
    const coloured = wrapper.findAll('.compact-value')

    expect(coloured.map(text => text.classes().filter(name => name.startsWith('text-')))).toEqual([
      [],
      ['text-gain'],
      ['text-gain'],
      ['text-loss'],
    ])
  })

  it('should leave out the rows of a best and a worst day that do not exist', () => {
    const wrapper = createWrapper([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-03', 1300),
    ])

    expect(rowsOf(wrapper)).toEqual([
      ['Flat days', 'under ±0.05%', '0'],
      ['Average day', '+€300.00'],
    ])
  })
})
