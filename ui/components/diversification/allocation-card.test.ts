import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationCard from './allocation-card.vue'
import { Currency, RebalanceStatus, type EtfDetailDto } from '../../models/generated/domain-models'

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
        isBuy: true,
        computedUnits: 0,
      },
    })
    expect(wrapper.text()).toContain('Action')
    expect(wrapper.text()).not.toContain('Buy')
    expect(wrapper.text()).not.toContain('Sell')
  })
})

describe('AllocationCard rebalance status display', () => {
  it('applies card-rebalance class when status is REBALANCE and showRebalanceMode is on', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        rebalanceStatus: RebalanceStatus.REBALANCE,
        relDrift: 27,
      },
    })
    expect(wrapper.find('.allocation-card').classes()).toContain('card-rebalance')
  })

  it('applies card-drifting class when status is DRIFTING', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        rebalanceStatus: RebalanceStatus.DRIFTING,
        relDrift: 12,
      },
    })
    expect(wrapper.find('.allocation-card').classes()).toContain('card-drifting')
  })

  it('does not apply tint class when showRebalanceMode is false', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: false,
        rebalanceStatus: RebalanceStatus.REBALANCE,
        relDrift: 27,
      },
    })
    const classes = wrapper.find('.allocation-card').classes()
    expect(classes).not.toContain('card-rebalance')
    expect(classes).not.toContain('card-drifting')
  })

  it('renders Drift metric for non-OK status', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        rebalanceStatus: RebalanceStatus.REBALANCE,
        relDrift: 27,
      },
    })
    expect(wrapper.find('.drift-metric').exists()).toBe(true)
    expect(wrapper.find('.drift-metric').text()).toContain('+27%')
  })

  it('hides Drift metric when status is OK', () => {
    const wrapper = mount(AllocationCard, {
      props: {
        ...baseProps,
        showRebalanceMode: true,
        rebalanceStatus: RebalanceStatus.OK,
        relDrift: 0,
      },
    })
    expect(wrapper.find('.drift-metric').exists()).toBe(false)
  })
})
