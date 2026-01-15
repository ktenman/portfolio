import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DiversificationStats from './diversification-stats.vue'

describe('DiversificationStats', () => {
  const defaultProps = {
    weightedTer: 0.22,
    weightedAnnualReturn: 0.12,
    totalUniqueHoldings: 3500,
    top10Percentage: 18.5,
  }

  describe('rendering', () => {
    it('should render all four stat cards', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const statCards = wrapper.findAll('.stat-card')
      expect(statCards).toHaveLength(4)
    })

    it('should display stat labels correctly', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const labels = wrapper.findAll('.stat-label')
      expect(labels[0].text()).toBe('Weighted TER')
      expect(labels[1].text()).toBe('Weighted Return')
      expect(labels[2].text()).toBe('Unique Holdings')
      expect(labels[3].text()).toBe('Top 10 Concentration')
    })
  })

  describe('weighted TER formatting', () => {
    it('should format weighted TER with 3 decimal places', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const values = wrapper.findAll('.stat-value')
      expect(values[0].text()).toBe('0.220%')
    })

    it('should handle zero TER', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, weightedTer: 0 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[0].text()).toBe('0.000%')
    })
  })

  describe('weighted annual return formatting', () => {
    it('should format annual return as percentage', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const values = wrapper.findAll('.stat-value')
      expect(values[1].text()).toBe('12.00%')
    })

    it('should handle negative annual return', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, weightedAnnualReturn: -0.05 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[1].text()).toBe('-5.00%')
    })

    it('should handle zero annual return', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, weightedAnnualReturn: 0 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[1].text()).toBe('0.00%')
    })
  })

  describe('unique holdings formatting', () => {
    it('should format unique holdings with locale string', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const values = wrapper.findAll('.stat-value')
      expect(values[2].text()).toBe('3,500')
    })

    it('should handle small number of holdings', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, totalUniqueHoldings: 50 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[2].text()).toBe('50')
    })

    it('should handle zero holdings', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, totalUniqueHoldings: 0 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[2].text()).toBe('0')
    })
  })

  describe('top 10 concentration formatting', () => {
    it('should format concentration as percentage', () => {
      const wrapper = mount(DiversificationStats, { props: defaultProps })
      const values = wrapper.findAll('.stat-value')
      expect(values[3].text()).toBe('18.50%')
    })

    it('should handle high concentration', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, top10Percentage: 75.25 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[3].text()).toBe('75.25%')
    })

    it('should handle zero concentration', () => {
      const wrapper = mount(DiversificationStats, {
        props: { ...defaultProps, top10Percentage: 0 },
      })
      const values = wrapper.findAll('.stat-value')
      expect(values[3].text()).toBe('0.00%')
    })
  })
})
