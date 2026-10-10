import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DailyProfitCell from './daily-profit-cell.vue'
import DailyProfitFigures from './daily-profit-figures.vue'
import {
  createCalendarRow,
  createCalendarRows,
  createDailyProfitCalendar,
} from '../../tests/fixtures'

const createWrapper = (rows = createCalendarRows()) =>
  mount(DailyProfitFigures, { props: { calendar: createDailyProfitCalendar(rows) } })

const buttonTexts = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper
    .findAll('button')
    .map(button => button.findAll('.extreme-content > span').map(span => span.text()))

describe('DailyProfitFigures', () => {
  it('should show the four figures of the period', () => {
    const figures = createWrapper()
      .findAll('.figure')
      .map(figure => [figure.find('dt').text(), figure.find('dd').text()])

    expect(figures).toEqual([
      ['Up days', '4'],
      ['Down days', '2'],
      ['Flat days', '1 under ±0.05%'],
      ['Average day', '+€225.71'],
    ])
  })

  it.each([
    [[1000, 1300], 'text-gain', '+€300.00'],
    [[1000, 700], 'text-loss', '−€300.00'],
    [[1000, 1000], '', '€0.00'],
  ])('should colour an average day of %s as %s', (profits, colour, printed) => {
    const wrapper = createWrapper([
      createCalendarRow('2025-12-01', profits[0]),
      createCalendarRow('2025-12-02', profits[1]),
    ])
    const average = wrapper.findAll('.figure dd')[3]

    expect([average.text(), average.classes().filter(name => name.startsWith('text-'))]).toEqual([
      printed,
      colour ? [colour] : [],
    ])
  })

  it('should offer the best and the worst day with a swatch, the amount and the date', () => {
    const wrapper = createWrapper()

    expect(buttonTexts(wrapper)).toEqual([
      ['', 'Best day', '+€700.00', '9 Dec'],
      ['', 'Worst day', '−€600.00', '3 Dec'],
    ])
    expect(wrapper.findAllComponents(DailyProfitCell).map(cell => cell.props('variant'))).toEqual([
      'gain-4',
      'loss-4',
    ])
    expect(
      wrapper.findAll('button .text-gain, button .text-loss').map(amount => amount.text())
    ).toEqual(['+€700.00', '−€600.00'])
  })

  it('should emit the date of the day its button names', async () => {
    const wrapper = createWrapper()
    const [best, worst] = wrapper.findAll('button')

    await worst.trigger('click')
    await best.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12-03'], ['2025-12-09']])
  })

  it('should leave out the Worst day button when no single day has a loss', () => {
    const wrapper = createWrapper(createCalendarRows().slice(0, 2))

    expect(buttonTexts(wrapper)).toEqual([['', 'Best day', '+€300.00', '2 Dec']])
  })

  it('should leave out the Best day button when no single day has a gain', () => {
    const wrapper = createWrapper(createCalendarRows().slice(1, 3))

    expect(buttonTexts(wrapper)).toEqual([['', 'Worst day', '−€600.00', '3 Dec']])
  })

  it('should leave out both buttons when the period has no single day', () => {
    const wrapper = createWrapper([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-03', 1300),
    ])

    expect(wrapper.find('.extreme-buttons').exists()).toBe(false)
    expect(wrapper.findAll('.figure dd')[0].text()).toBe('1')
  })
})
