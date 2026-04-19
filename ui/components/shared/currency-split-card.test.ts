import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CurrencySplitCard from './currency-split-card.vue'

describe('currency-split-card', () => {
  it('renders nothing when entries empty', () => {
    const wrapper = mount(CurrencySplitCard, { props: { entries: [] } })
    expect(wrapper.find('.currency-split-card').exists()).toBe(false)
  })

  it('renders one row per entry, sorted descending by value', () => {
    const wrapper = mount(CurrencySplitCard, {
      props: {
        entries: [
          { currency: 'EUR', value: 20 },
          { currency: 'USD', value: 80 },
        ],
      },
    })
    const rows = wrapper.findAll('.currency-split-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('USD')
    expect(rows[0].text()).toContain('80.0%')
    expect(rows[1].text()).toContain('EUR')
    expect(rows[1].text()).toContain('20.0%')
  })

  it('shows values when showValue is true', () => {
    const wrapper = mount(CurrencySplitCard, {
      props: {
        entries: [{ currency: 'USD', value: 1234.56 }],
        showValue: true,
        formatValue: (v: number) => `€${v.toFixed(0)}`,
      },
    })
    expect(wrapper.find('.currency-value').text()).toBe('€1235')
  })

  it('hides values when showValue is false', () => {
    const wrapper = mount(CurrencySplitCard, {
      props: {
        entries: [{ currency: 'USD', value: 100 }],
        showValue: false,
      },
    })
    expect(wrapper.find('.currency-value').exists()).toBe(false)
  })

  it('uses default label when not provided', () => {
    const wrapper = mount(CurrencySplitCard, {
      props: { entries: [{ currency: 'USD', value: 1 }] },
    })
    expect(wrapper.find('.currency-split-label').text()).toBe('Currency Split')
  })

  it('uses custom label when provided', () => {
    const wrapper = mount(CurrencySplitCard, {
      props: { entries: [{ currency: 'USD', value: 1 }], label: 'Fund Currency' },
    })
    expect(wrapper.find('.currency-split-label').text()).toBe('Fund Currency')
  })
})
