import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownHeader from './etf-breakdown-header.vue'

describe('EtfBreakdownHeader', () => {
  describe('header content', () => {
    it('should show all ETFs description when all are selected', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings across all two ETF positions'
      )
    })

    it('should show single ETF description when one is selected', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings from QDVE'
      )
    })

    it('should show two ETFs description when two are selected', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR', 'XAIX:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings from QDVE and VUAA'
      )
    })

    it('should show three ETFs with Oxford comma', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR', 'XAIX:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR', 'XAIX:GER:EUR', 'IITU:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings from QDVE, VUAA, and XAIX'
      )
    })

    it('should show count for more than three ETFs', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR', 'VUAA:GER:EUR', 'XAIX:GER:EUR', 'IITU:GER:EUR'],
          availableEtfs: [
            'QDVE:GER:EUR',
            'VUAA:GER:EUR',
            'XAIX:GER:EUR',
            'IITU:GER:EUR',
            'VWCE:GER:EUR',
          ],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings from four selected ETF positions'
      )
    })

    it('should handle singular ETF in all selected state', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: ['QDVE:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings across all one ETF position'
      )
    })
  })

  describe('stat cards', () => {
    it('should display stat cards when totalValue is greater than 0', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 10000,
          uniqueHoldings: 50,
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
        },
      })

      expect(wrapper.find('.header-right').exists()).toBe(false)
    })

    it('should format total value as currency', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 12345.67,
          uniqueHoldings: 50,
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
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
          selectedEtfs: [],
          availableEtfs: [],
        },
      })

      expect(wrapper.find('.header-right').exists()).toBe(false)
    })

    it('should handle zero unique holdings', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
          totalValue: 1000,
          uniqueHoldings: 0,
          selectedEtfs: [],
          availableEtfs: [],
        },
      })

      const statValues = wrapper.findAll('.stat-value')
      expect(statValues[1].text()).toBe('0')
    })
  })
})
