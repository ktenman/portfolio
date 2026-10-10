import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DailyProfitCompact from './daily-profit-compact.vue'
import DailyProfitGrid from './daily-profit-grid.vue'
import {
  createCalendarRow,
  createCalendarRows,
  createDailyProfitCalendar,
} from '../../tests/fixtures'

const createWrapper = (rows = createCalendarRows(), today?: string) =>
  mount(DailyProfitCompact, { props: { calendar: createDailyProfitCalendar(rows, today) } })

const rowsOf = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper.findAll('.compact-row').map(row => row.findAll('p, span').map(text => text.text()))

describe('DailyProfitCompact', () => {
  it('should head the module with the starting day and the two counts', () => {
    const wrapper = createWrapper()

    expect([wrapper.find('h2').text(), wrapper.find('.compact-counts').text()]).toEqual([
      'Every day since 1 Dec',
      '4 up · 2 down',
    ])
  })

  it('should draw the grid in compact mode', () => {
    const grid = createWrapper().findComponent(DailyProfitGrid)

    expect([grid.props('compact'), grid.props('selected')]).toEqual([true, undefined])
  })

  it('should list the flat days, the average day, the best day and the worst day', () => {
    expect(rowsOf(createWrapper())).toEqual([
      ['Flat days', 'under ±0.05%', '1'],
      ['Average day', '+€225.71'],
      ['Best day · Tue 9 Dec', 'Closed at €100,000.00', '+€700.00', '+0.70%'],
      ['Worst day · Wed 3 Dec', 'Closed at €100,000.00', '−€600.00', '−0.60%'],
    ])
  })

  it('should print gains and losses in their colours', () => {
    const wrapper = createWrapper()
    const coloured = [...wrapper.findAll('.compact-value'), ...wrapper.findAll('.compact-change')]

    expect(coloured.map(text => text.classes().filter(name => name.startsWith('text-')))).toEqual([
      [],
      ['text-gain'],
      ['text-gain'],
      ['text-loss'],
    ])
  })

  it('should read Now at while the best day is today', () => {
    const wrapper = createWrapper(createCalendarRows(), '2025-12-09')

    expect(rowsOf(wrapper)[2]).toEqual([
      'Best day · Tue 9 Dec',
      'Now at €100,000.00',
      '+€700.00',
      '+0.70%',
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

  it('should leave the percentage out of a best day that has none', () => {
    const wrapper = createWrapper([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-02', 1300, 200),
    ])

    expect(rowsOf(wrapper)[2]).toEqual(['Best day · Tue 2 Dec', 'Closed at €200.00', '+€300.00'])
  })
})
