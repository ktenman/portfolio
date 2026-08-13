import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownHeader from './etf-breakdown-header.vue'

describe('EtfBreakdownHeader', () => {
  describe('header content', () => {
    it('should show all ETFs description when all are selected', () => {
      const wrapper = mount(EtfBreakdownHeader, {
        props: {
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
          selectedEtfs: ['QDVE:GER:EUR'],
          availableEtfs: ['QDVE:GER:EUR'],
        },
      })

      expect(wrapper.find('.page-subtitle').text()).toBe(
        'Aggregated view of underlying holdings across all one ETF position'
      )
    })
  })
})
