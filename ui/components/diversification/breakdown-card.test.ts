import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BreakdownCard from './breakdown-card.vue'

describe('BreakdownCard', () => {
  const defaultItems = [
    { key: 'apple', name: 'Apple Inc', percentage: 5.5 },
    { key: 'microsoft', name: 'Microsoft Corp', percentage: 4.8 },
    { key: 'amazon', name: 'Amazon.com Inc', percentage: 3.2 },
  ]

  describe('rendering', () => {
    it('should render the title', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Top Holdings', items: defaultItems },
      })
      expect(wrapper.find('.breakdown-title').text()).toBe('Top Holdings')
    })

    it('should render all items', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: defaultItems },
      })
      const items = wrapper.findAll('.breakdown-item')
      expect(items).toHaveLength(3)
    })

    it('should display item names', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: defaultItems },
      })
      const names = wrapper.findAll('.breakdown-name')
      expect(names[0].text()).toBe('Apple Inc')
      expect(names[1].text()).toBe('Microsoft Corp')
      expect(names[2].text()).toBe('Amazon.com Inc')
    })

    it('should format percentages correctly', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: defaultItems },
      })
      const values = wrapper.findAll('.breakdown-value')
      expect(values[0].text()).toBe('5.50%')
      expect(values[1].text()).toBe('4.80%')
      expect(values[2].text()).toBe('3.20%')
    })
  })

  describe('maxItems prop', () => {
    it('should limit items to maxItems when specified', () => {
      const manyItems = Array.from({ length: 20 }, (_, i) => ({
        key: `item-${i}`,
        name: `Item ${i}`,
        percentage: 5 - i * 0.2,
      }))
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: manyItems, maxItems: 5 },
      })
      const items = wrapper.findAll('.breakdown-item')
      expect(items).toHaveLength(5)
    })

    it('should use default maxItems of 15', () => {
      const manyItems = Array.from({ length: 20 }, (_, i) => ({
        key: `item-${i}`,
        name: `Item ${i}`,
        percentage: 5 - i * 0.2,
      }))
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: manyItems },
      })
      const items = wrapper.findAll('.breakdown-item')
      expect(items).toHaveLength(15)
    })

    it('should show all items when less than maxItems', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items: defaultItems, maxItems: 10 },
      })
      const items = wrapper.findAll('.breakdown-item')
      expect(items).toHaveLength(3)
    })
  })

  describe('empty state', () => {
    it('should render title with empty items', () => {
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Empty Section', items: [] },
      })
      expect(wrapper.find('.breakdown-title').text()).toBe('Empty Section')
      expect(wrapper.findAll('.breakdown-item')).toHaveLength(0)
    })
  })

  describe('percentage formatting edge cases', () => {
    it('should format zero percentage', () => {
      const items = [{ key: 'zero', name: 'Zero Item', percentage: 0 }]
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items },
      })
      expect(wrapper.find('.breakdown-value').text()).toBe('0.00%')
    })

    it('should format small percentages', () => {
      const items = [{ key: 'small', name: 'Small Item', percentage: 0.01 }]
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items },
      })
      expect(wrapper.find('.breakdown-value').text()).toBe('0.01%')
    })

    it('should format large percentages', () => {
      const items = [{ key: 'large', name: 'Large Item', percentage: 99.99 }]
      const wrapper = mount(BreakdownCard, {
        props: { title: 'Holdings', items },
      })
      expect(wrapper.find('.breakdown-value').text()).toBe('99.99%')
    })
  })

  describe('different titles', () => {
    it('should render Sectors title', () => {
      const wrapper = mount(BreakdownCard, {
        props: {
          title: 'Sectors',
          items: [{ key: 'tech', name: 'Technology', percentage: 25.5 }],
        },
      })
      expect(wrapper.find('.breakdown-title').text()).toBe('Sectors')
    })

    it('should render Countries title', () => {
      const wrapper = mount(BreakdownCard, {
        props: {
          title: 'Countries',
          items: [{ key: 'us', name: 'United States', percentage: 60.0 }],
        },
      })
      expect(wrapper.find('.breakdown-title').text()).toBe('Countries')
    })
  })
})
