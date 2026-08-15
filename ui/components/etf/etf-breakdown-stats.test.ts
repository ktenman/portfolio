import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownStats from './etf-breakdown-stats.vue'

describe('EtfBreakdownStats', () => {
  describe('stat cards', () => {
    it('should display stat cards when totalValue is greater than 0', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50 },
      })

      const statCards = wrapper.findAll('.stat-card')
      expect(statCards).toHaveLength(4)
    })

    it('should not display stat cards when totalValue is 0', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 0, uniqueHoldings: 0 },
      })

      expect(wrapper.find('.breakdown-stats').exists()).toBe(false)
    })

    it('should format total value as currency', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 12345.67, uniqueHoldings: 50 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€12,345.67')
    })

    it('should display unique holdings count', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 42 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[1].text()).toBe('42')
    })

    it('should display stat labels correctly', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50 },
      })

      const statLabels = wrapper.findAll('.stat-label')
      expect(statLabels[0].text()).toBe('Total Value')
      expect(statLabels[1].text()).toBe('Unique Holdings')
      expect(statLabels[2].text()).toBe('Weighted TER')
      expect(statLabels[3].text()).toBe('Weighted Return')
    })

    it('should display the weighted TER with three decimals', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50, weightedTer: 0.3125 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[2].text()).toBe('0.313%')
    })

    it('should display the weighted return as a percentage', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50, weightedAnnualReturn: 0.4063 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[3].text()).toBe('40.63%')
    })

    it('should display a dash when weighted figures are unavailable', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues.slice(2).map(value => value.text())).toEqual(['-', '-'])
    })

    it('should display the currency split card when entries are given', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          currencySplit: [{ currency: 'USD', value: 7500 }],
        },
      })

      expect(wrapper.find('.currency-split-card').exists()).toBe(true)
    })

    it('should dont display the currency split card when entries are empty', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 10000, uniqueHoldings: 50, currencySplit: [] },
      })

      expect(wrapper.find('.currency-split-card').exists()).toBe(false)
    })
  })

  describe('currency formatting', () => {
    it('should format large numbers correctly', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 1234567.89, uniqueHoldings: 100 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€1,234,567.89')
    })

    it('should format small numbers correctly', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 0.99, uniqueHoldings: 1 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€0.99')
    })

    it('should always show 2 decimal places', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 1000, uniqueHoldings: 5 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€1,000.00')
    })
  })

  describe('edge cases', () => {
    it('should handle negative total value', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: -500, uniqueHoldings: 10 },
      })

      expect(wrapper.find('.breakdown-stats').exists()).toBe(false)
    })

    it('should handle zero unique holdings', () => {
      const wrapper = mount(EtfBreakdownStats, {
        props: { totalValue: 1000, uniqueHoldings: 0 },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[1].text()).toBe('0')
    })
  })
})
