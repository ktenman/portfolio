import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { buildMonthlyProfit } from '../../services/monthly-profit'
import { createCalendarRow } from '../../tests/fixtures'
import MonthlyProfitCompact from './monthly-profit-compact.vue'

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

describe('MonthlyProfitCompact', () => {
  it('should keep the average currency text contiguous across its cents styling', () => {
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar: createCalendar() } })

    expect(wrapper.findAll('.monthly-profit-compact-value')[1].text()).toBe('+€25.00')
  })

  it('should show the heading, full span, counts and newest years first', () => {
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar: createCalendar() } })

    expect(wrapper.find('h2').text()).toBe('Monthly profit')
    expect(wrapper.find('.monthly-profit-compact-counts').text()).toBe('0 up · 1 down')
    expect(wrapper.find('.monthly-profit-span').text()).toBe('Dec 2025 – Jan 2026')
    expect(wrapper.findAll('.monthly-profit-year-label').map(label => label.text())).toEqual([
      '2026',
      '2025',
    ])
  })

  it('should keep month dots decorative and untouched by clicks or touch preview', async () => {
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar: createCalendar() } })
    const dots = wrapper.findAll('.monthly-profit-dot')

    await dots[0].trigger('click')
    await dots[0].trigger('pointerenter', { pointerType: 'touch' })

    expect(dots).toHaveLength(24)
    expect(dots.every(dot => dot.attributes('aria-hidden') === 'true')).toBe(true)
    expect(wrapper.findAll('button, [tabindex], [role="application"]')).toHaveLength(0)
    expect(wrapper.emitted('select')).toBeUndefined()
    expect(wrapper.emitted('preview')).toBeUndefined()
  })

  it('should show complete year percentages with coverage in accessible names and visible notes', () => {
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar: createCalendar() } })
    const totals = wrapper.findAll('.monthly-profit-year-total')

    expect(totals.map(total => total.text())).toEqual(['−0.1%', '−3.0%'])
    expect(totals[0].attributes('aria-label')).toContain('YTD to 12 Jan 2026')
    expect(totals[1].attributes('aria-label')).toContain('+€60.00')
    expect(wrapper.text()).toContain('2026 · YTD to 12 Jan 2026')
  })

  it('should keep euro and percentage signs independent in Best and Worst figures', () => {
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar: createCalendar() } })
    const [best, worst] = wrapper.findAll('.monthly-profit-compact-extreme')

    expect(best.text()).toContain('Best month · Dec 2025')
    expect(best.text()).toContain('Closed at €10,100.00 · as of 31 Dec 2025')
    expect(best.find('.monthly-profit-compact-amount').text()).toBe('+€60.00')
    expect(best.find('.monthly-profit-compact-amount').classes()).toContain('text-gain')
    expect(best.find('.monthly-profit-compact-percent').text()).toBe('−3.0%')
    expect(best.find('.monthly-profit-compact-percent').classes()).toContain('text-loss')
    expect(worst.text()).toContain('Worst month · Jan 2026')
    expect(worst.text()).toContain('Now at €9,990.00')
    expect(worst.text()).toContain('to 12 Jan')
  })

  it('should explain an unavailable percentage beside its preserved euro figure', () => {
    const calendar = buildMonthlyProfit(
      [createCalendarRow('2025-11-30', 1000, 100), createCalendarRow('2025-12-31', 1300, 200)],
      '2026-01-01'
    )
    if (!calendar) throw new Error('Expected unavailable percentage history')
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar } })
    const best = wrapper.find('.monthly-profit-compact-extreme')

    expect(best.find('.monthly-profit-compact-amount').text()).toBe('+€300.00')
    expect(best.find('.monthly-profit-compact-percent').text()).toBe('—')
    expect(best.text()).toContain('Percentage unavailable')
  })

  it('should omit Best and Worst when only spans across months are observed', () => {
    const calendar = buildMonthlyProfit(
      [
        createCalendarRow('2025-01-15', 1000),
        createCalendarRow('2025-03-31', 1300),
        createCalendarRow('2025-05-31', 1500),
      ],
      '2026-01-01'
    )
    if (!calendar) throw new Error('Expected spanning history')
    const wrapper = mount(MonthlyProfitCompact, { props: { calendar } })

    expect(wrapper.findAll('.monthly-profit-compact-extreme')).toHaveLength(0)
    expect(wrapper.text()).toContain('Average month—')
    expect(wrapper.text()).toContain(
      '2 intervals excluded from monthly statistics because they cover multiple months.'
    )
    expect(wrapper.text()).toContain('No daily summary')
  })
})
