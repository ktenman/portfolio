import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationCard from './allocation-card.vue'
import { Currency, type EtfDetailDto } from '../../models/generated/domain-models'

const etf: EtfDetailDto = {
  instrumentId: 1,
  symbol: 'VUAA',
  name: 'Vanguard S&P 500',
  allocation: 0,
  ter: 0.07,
  annualReturn: 0.15,
  currentPrice: 100,
  fundCurrency: Currency.EUR,
}

const baseProps = {
  allocation: { instrumentId: 1, value: 50, currentValue: 500 },
  availableEtfs: [etf],
  totalInvestment: 0,
  disableRemove: false,
}

describe('AllocationCard action label', () => {
  it('shows Units label in investment mode', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        totalInvestment: 1000,
        showRebalanceMode: false,
        computedUnits: 5,
      },
    })
    expect(wrapper.text()).toContain('Units')
    expect(wrapper.text()).not.toContain('Action')
  })

  it('shows Buy label in rebalance mode when action is present', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        hasAction: true,
        isBuy: true,
        computedUnits: 3,
      },
    })
    expect(wrapper.text()).toContain('Buy')
    expect(wrapper.text()).not.toContain('Action')
  })

  it('shows Sell label in rebalance mode when action is present', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        hasAction: true,
        isBuy: false,
        computedUnits: 2,
      },
    })
    expect(wrapper.text()).toContain('Sell')
    expect(wrapper.text()).not.toContain('Action')
  })

  it('shows neutral Action label in rebalance mode when no action', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        hasAction: false,
        isBuy: true,
        computedUnits: 0,
      },
    })
    expect(wrapper.text()).toContain('Action')
    expect(wrapper.text()).not.toContain('Buy')
    expect(wrapper.text()).not.toContain('Sell')
  })
})
