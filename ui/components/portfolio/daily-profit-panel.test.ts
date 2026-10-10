import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DailyProfitPanel from './daily-profit-panel.vue'
import { createCalendarRow, createDailyProfitCalendar } from '../../tests/fixtures'
import { dayOn } from '../../services/daily-profit-calendar'

const createWrapper = (
  date: string,
  ends: { previous?: string | null; next?: string | null } = {}
) => {
  const day = dayOn(createDailyProfitCalendar(), date)
  if (!day) throw new Error('expected a day')
  return mount(DailyProfitPanel, {
    props: { day, previous: '2025-12-01', next: '2025-12-03', ...ends },
  })
}

const textsOf = (wrapper: ReturnType<typeof createWrapper>, selectors: string[]) =>
  selectors.map(selector =>
    wrapper.find(selector).exists() ? wrapper.find(selector).text() : null
  )

const TEXTS = ['.day-label', '.day-amount', '.day-percent', '.day-detail', '.day-note']

describe('DailyProfitPanel', () => {
  it('should show a ranked gain with its date, amount, percentage and rank line', () => {
    const wrapper = createWrapper('2025-12-09')

    expect(textsOf(wrapper, TEXTS)).toEqual([
      'Tue 9 Dec 2025',
      '+€700.00',
      '+0.70%',
      'Best of 6 single days · closed at €100,000.00 · XIRR 20.69%',
      null,
    ])
    expect(wrapper.find('.day-change').classes()).toContain('text-gain')
  })

  it('should show a loss in the loss colour with a minus sign', () => {
    const wrapper = createWrapper('2025-12-03')

    expect(textsOf(wrapper, ['.day-amount', '.day-percent'])).toEqual(['−€600.00', '−0.60%'])
    expect(wrapper.find('.day-change').classes()).toContain('text-loss')
  })

  it('should add the note of a day that follows a missing day', () => {
    const wrapper = createWrapper('2025-12-06')

    expect(textsOf(wrapper, TEXTS)).toEqual([
      'Sat 6 Dec 2025',
      '+€1,200.00',
      '+1.21%',
      'Closed at €100,000.00 · XIRR 20.69%',
      'Covers 2 days, no daily summary for 5 Dec',
    ])
  })

  it('should show the starting day without a change or a percentage', () => {
    const wrapper = createWrapper('2025-12-01')

    expect(textsOf(wrapper, TEXTS)).toEqual([
      'Mon 1 Dec 2025',
      null,
      null,
      'Closed at €100,000.00 · XIRR 20.69%',
      'Starting day, not counted or ranked',
    ])
    expect(wrapper.find('.day-change').exists()).toBe(true)
  })

  it('should leave the percentage out when the day has none', () => {
    const calendar = createDailyProfitCalendar([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-02', 1300, 200),
    ])
    const wrapper = mount(DailyProfitPanel, {
      props: { day: calendar.days[1], previous: '2025-12-01', next: null },
    })

    expect(textsOf(wrapper, ['.day-amount', '.day-percent'])).toEqual(['+€300.00', null])
    expect(wrapper.text()).not.toMatch(/NaN|Infinity/)
  })

  it('should name the two day buttons and give them a tooltip', () => {
    const buttons = createWrapper('2025-12-02').findAll('button.day-btn')

    expect(
      buttons.map(button => [
        button.attributes('aria-label'),
        button.attributes('title'),
        button.attributes('type'),
      ])
    ).toEqual([
      ['Previous day', 'Previous day', 'button'],
      ['Next day', 'Next day', 'button'],
    ])
  })

  it('should emit the day before and the day after', async () => {
    const wrapper = createWrapper('2025-12-02')
    const [previous, next] = wrapper.findAll('.day-btn')

    await previous.trigger('click')
    await next.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12-01'], ['2025-12-03']])
  })

  it('should keep a day button focusable but idle at the end of the period', async () => {
    const wrapper = createWrapper('2025-12-09', { next: null })
    const [previous, next] = wrapper.findAll('.day-btn')

    await next.trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
    expect([next.attributes('aria-disabled'), next.attributes('disabled')]).toEqual([
      'true',
      undefined,
    ])
    expect(previous.attributes('aria-disabled')).toBeUndefined()
  })
})
