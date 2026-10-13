import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { ProfitMonth } from '../../services/monthly-profit'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import MonthlyProfitPanel from './monthly-profit-panel.vue'

enableAutoUnmount(afterEach)

const createMonth = (overrides: Partial<ProfitMonth> = {}): ProfitMonth => ({
  key: '2025-12',
  opening: createPortfolioSummaryDto({ date: '2025-11-30', totalProfit: 1000 }),
  closing: createPortfolioSummaryDto({
    date: '2025-12-31',
    totalValue: 178204.06,
    totalProfit: 1060,
    xirrAnnualReturn: 0.2118,
  }),
  rawChange: 60,
  change: 60,
  percent: -3,
  factor: 0.97,
  step: 'loss-3',
  eligible: true,
  coverage: ['No daily summary for 30 Dec'],
  name: 'December 2025, +€60.00, −3.0%, No daily summary for 30 Dec',
  rank: '3rd best of 7 months',
  closingLabel: 'Closed at €178,204.06 · as of 31 Dec 2025',
  isCurrent: false,
  ...overrides,
})

const createWrapper = (overrides: Partial<ProfitMonth> = {}) =>
  mount(MonthlyProfitPanel, {
    props: { month: createMonth(overrides), previous: '2025-11', next: null },
    attachTo: document.body,
  })

describe('MonthlyProfitPanel', () => {
  it('should show the complete month, rank, closing value, XIRR and coverage', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('December 2025')
    expect(wrapper.text()).toContain('3rd best of 7 months')
    expect(wrapper.text()).toContain('by euro profit')
    expect(wrapper.text()).toContain('Closed at €178,204.06 · as of 31 Dec 2025')
    expect(wrapper.text()).toContain('XIRR 21.18%')
    expect(wrapper.text()).toContain('No daily summary for 30 Dec')
  })

  it('should color opposite euro and percentage signs independently', () => {
    const wrapper = createWrapper()

    expect(wrapper.find('.monthly-profit-amount').text()).toBe('+€60.00')
    expect(wrapper.find('.monthly-profit-amount').classes()).toContain('text-gain')
    expect(wrapper.find('.monthly-profit-percent').text()).toBe('−3.0%')
    expect(wrapper.find('.monthly-profit-percent').classes()).toContain('text-loss')
    expect(wrapper.find('.monthly-profit-change').classes()).not.toContain('text-gain')
  })

  it('should retain a valid percentage when the euro change is zero', () => {
    const wrapper = createWrapper({ rawChange: 0, change: 0, percent: 1.2, factor: 1.012 })

    expect(wrapper.find('.monthly-profit-amount').text()).toBe('€0.00')
    expect(wrapper.find('.monthly-profit-percent').text()).toBe('+1.2%')
    expect(wrapper.find('.monthly-profit-percent').classes()).toContain('text-gain')
  })

  it('should explain unavailable percentages while retaining the euro result', () => {
    const wrapper = createWrapper({ percent: null, factor: null, step: 'gain-1' })

    expect(wrapper.text()).toContain('+€60.00')
    expect(wrapper.find('.monthly-profit-percent').text()).toBe('—')
    expect(wrapper.text()).toContain('Percentage unavailable')
    expect(wrapper.find('.monthly-profit-percent').classes()).not.toContain('text-gain')
    expect(wrapper.text()).not.toMatch(/NaN|Infinity/)
  })

  it('should preserve the current closing label on a provisional month', () => {
    const wrapper = createWrapper({
      closingLabel: 'Now at €178,204.06',
      isCurrent: true,
      coverage: ['to 31 Dec'],
    })

    expect(wrapper.text()).toContain('Now at €178,204.06')
    expect(wrapper.text()).toContain('to 31 Dec')
    expect(wrapper.text()).not.toContain('as of')
  })

  it('should move to the previous month without stealing button focus', async () => {
    const wrapper = createWrapper()
    const previous = wrapper.find('button[aria-label="Previous month"]')
    ;(previous.element as HTMLElement).focus()

    await previous.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-11']])
    expect(document.activeElement).toBe(previous.element)
  })

  it('should keep a boundary arrow focusable and idle', async () => {
    const wrapper = createWrapper()
    const next = wrapper.find('button[aria-label="Next month"]')
    ;(next.element as HTMLElement).focus()

    await next.trigger('click')

    expect(next.attributes('aria-disabled')).toBe('true')
    expect(next.attributes('disabled')).toBeUndefined()
    expect(wrapper.emitted('select')).toBeUndefined()
    expect(document.activeElement).toBe(next.element)
  })

  it.each([Number.NaN, Number.POSITIVE_INFINITY])(
    'should show an unavailable euro result for %s',
    change => {
      const wrapper = createWrapper({
        change,
        rawChange: change,
        percent: null,
        factor: null,
        rank: '',
      })

      expect(wrapper.find('.monthly-profit-amount').text()).toBe('—')
      expect(wrapper.find('.monthly-profit-amount').classes()).not.toContain('text-loss')
      expect(wrapper.text()).not.toMatch(/NaN|Infinity/)
      expect(wrapper.text()).toContain('Closed at €178,204.06')
    }
  )
})
