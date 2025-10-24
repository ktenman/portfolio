import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownHeader from './etf-breakdown-header.vue'

describe('EtfBreakdownHeader', () => {
  describe('header content', () => {
    it('should render title and subtitle', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
        },
      })

      expect(wrapper.find('.page-title').text()).toBe('ETF Holdings Breakdown')
      expect(wrapper.find('.page-subtitle').text()).toContain(
        'Aggregated view of your underlying holdings across all ETF positions'
      )
    })
  })

  describe('stat cards', () => {
    it('should display stat cards when totalValue is greater than 0', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
        },
      })

      const statCards = wrapper.findAll('.stat-card')
      expect(statCards).toHaveLength(2)
    })

    it('should not display stat cards when totalValue is 0', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 0,
          uniqueHoldings: 0,
        },
      })

      expect(wrapper.find('.header-right').exists()).toBe(false)
    })

    it('should format total value as currency', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 12345.67,
          uniqueHoldings: 50,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€12,345.67')
    })

    it('should display unique holdings count', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 42,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[1].text()).toBe('42')
    })

    it('should display stat labels correctly', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
        },
      })

      const statLabels = wrapper.findAll('.stat-label')
      expect(statLabels[0].text()).toBe('Total Value')
      expect(statLabels[1].text()).toBe('Unique Holdings')
    })
  })

  describe('currency formatting', () => {
    it('should format large numbers correctly', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 1234567.89,
          uniqueHoldings: 100,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€1,234,567.89')
    })

    it('should format small numbers correctly', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 0.99,
          uniqueHoldings: 1,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€0.99')
    })

    it('should always show 2 decimal places', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 1000,
          uniqueHoldings: 5,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[0].text()).toBe('€1,000.00')
    })
  })

  describe('edge cases', () => {
    it('should handle negative total value', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: -500,
          uniqueHoldings: 10,
        },
      })

      expect(wrapper.find('.header-right').exists()).toBe(false)
    })

    it('should handle zero unique holdings', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 1000,
          uniqueHoldings: 0,
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[1].text()).toBe('0')
    })
  })
})
