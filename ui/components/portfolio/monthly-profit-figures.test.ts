import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { buildMonthlyProfit } from '../../services/monthly-profit'
import { createCalendarRow } from '../../tests/fixtures'
import MonthlyProfitFigures from './monthly-profit-figures.vue'

enableAutoUnmount(afterEach)

const createCalendar = () => {
  const calendar = buildMonthlyProfit(
    [
      createCalendarRow('2025-11-30', 0, 1000),
      createCalendarRow('2025-12-01', -40, 960),
      createCalendarRow('2025-12-02', 60, 10100),
      createCalendarRow('2025-12-31', 60, 10100),
      createCalendarRow('2026-01-01', 50, 9990),
      createCalendarRow('2026-01-12', 50, 9990),
    ],
    '2026-01-12'
  )
  if (!calendar) throw new Error('Expected monthly history')
  return calendar
}

describe('MonthlyProfitFigures', () => {
  it('should show the classified counts and average euro change', () => {
    const wrapper = mount(MonthlyProfitFigures, { props: { calendar: createCalendar() } })

    expect(wrapper.findAll('.monthly-profit-figure').map(figure => figure.text())).toEqual([
      'Up months0',
      'Down months1',
      'Flat months1 under ±0.3%',
      'Average month+€25.00',
    ])
  })

  it('should commit Best and Worst shortcuts ranked by euro profit', async () => {
    const wrapper = mount(MonthlyProfitFigures, { props: { calendar: createCalendar() } })
    const [best, worst] = wrapper.findAll('.monthly-profit-extreme-btn')

    await best.trigger('click')
    await worst.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12'], ['2026-01']])
    expect(best.attributes('aria-label')).toContain('ranked by euro profit')
    expect(best.attributes('aria-label')).toContain('December 2025')
    expect(best.text()).toContain('+€60.00')
    expect(worst.text()).toContain('−€10.00')
  })

  it('should explain excluded spans and leave unavailable statistics empty', () => {
    const calendar = buildMonthlyProfit(
      [createCalendarRow('2025-01-31', 1000), createCalendarRow('2025-03-31', 1300)],
      '2026-01-01'
    )
    if (!calendar) throw new Error('Expected spanning history')
    const wrapper = mount(MonthlyProfitFigures, { props: { calendar } })

    expect(wrapper.text()).toContain('Average month—')
    expect(wrapper.text()).toContain(
      '1 interval excluded from monthly statistics because it covers multiple months.'
    )
    expect(wrapper.findAll('.monthly-profit-extreme-btn')).toHaveLength(0)
  })
})
