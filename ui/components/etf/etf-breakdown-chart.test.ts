import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import type { ChartDataItem } from './etf-breakdown-chart.vue'

vi.mock('chart.js', () => {
  const mockChart: any = vi.fn().mockImplementation(() => ({
    destroy: vi.fn(),
    update: vi.fn(),
  }))
  mockChart.register = vi.fn()

  return {
    Chart: mockChart,
    ArcElement: vi.fn(),
    Tooltip: vi.fn(),
    Legend: vi.fn(),
  }
})

describe('EtfBreakdownChart', () => {
  const mockChartData: ChartDataItem[] = [
    { label: 'Apple', value: 25.5, percentage: '25.50', color: '#0072B2' },
    { label: 'Microsoft', value: 20.3, percentage: '20.30', color: '#E69F00' },
    { label: 'Google', value: 15.2, percentage: '15.20', color: '#009E73' },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('should render chart title', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Top Companies',
          chartData: mockChartData,
        },
      })

      expect(wrapper.find('.chart-title').text()).toBe('Top Companies')
    })

    it('should render canvas element', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Sector Allocation',
          chartData: mockChartData,
        },
      })

      expect(wrapper.find('canvas').exists()).toBe(true)
    })

    it('should render legend items', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Test Chart',
          chartData: mockChartData,
        },
      })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(3)
    })
  })

  describe('legend content', () => {
    it('should display correct labels in legend', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Test Chart',
          chartData: mockChartData,
        },
      })

      const legendLabels = wrapper.findAll('.legend-label')
      expect(legendLabels[0].text()).toBe('Apple')
      expect(legendLabels[1].text()).toBe('Microsoft')
      expect(legendLabels[2].text()).toBe('Google')
    })

    it('should display correct percentages in legend', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Test Chart',
          chartData: mockChartData,
        },
      })

      const legendValues = wrapper.findAll('.legend-value')
      expect(legendValues[0].text()).toBe('25.50%')
      expect(legendValues[1].text()).toBe('20.30%')
      expect(legendValues[2].text()).toBe('15.20%')
    })

    it('should display correct colors in legend', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Test Chart',
          chartData: mockChartData,
        },
      })

      const legendColors = wrapper.findAll('.legend-color')
      expect(legendColors[0].attributes('style')).toContain('background-color: #0072B2')
      expect(legendColors[1].attributes('style')).toContain('background-color: #E69F00')
      expect(legendColors[2].attributes('style')).toContain('background-color: #009E73')
    })
  })

  describe('empty state', () => {
    it('should render without errors when chartData is empty', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Empty Chart',
          chartData: [],
        },
      })

      expect(wrapper.find('.chart-title').text()).toBe('Empty Chart')
      expect(wrapper.findAll('.legend-item')).toHaveLength(0)
    })
  })

  describe('data updates', () => {
    it('should update legend when chartData changes', async () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Test Chart',
          chartData: mockChartData,
        },
      })

      expect(wrapper.findAll('.legend-item')).toHaveLength(3)

      const newData: ChartDataItem[] = [
        { label: 'Amazon', value: 30, percentage: '30.00', color: '#D55E00' },
      ]

      await wrapper.setProps({ chartData: newData })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(1)
      expect(wrapper.find('.legend-label').text()).toBe('Amazon')
    })
  })

  describe('single item', () => {
    it('should render correctly with single item', () => {
      const singleItemData: ChartDataItem[] = [
        { label: 'Tesla', value: 100, percentage: '100.00', color: '#56B4E9' },
      ]

      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Single Item',
          chartData: singleItemData,
        },
      })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(1)
      expect(wrapper.find('.legend-label').text()).toBe('Tesla')
      expect(wrapper.find('.legend-value').text()).toBe('100.00%')
    })
  })

  describe('many items', () => {
    it('should render correctly with many items', () => {
      const manyItems: ChartDataItem[] = Array.from({ length: 10 }, (_, i) => ({
        label: `Item ${i + 1}`,
        value: 10,
        percentage: '10.00',
        color: '#000000',
      }))

      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Many Items',
          chartData: manyItems,
        },
      })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(10)
    })
  })

  describe('special characters', () => {
    it('should handle labels with special characters', () => {
      const specialData: ChartDataItem[] = [
        { label: 'AT&T Inc.', value: 25, percentage: '25.00', color: '#0072B2' },
        { label: 'Johnson & Johnson', value: 30, percentage: '30.00', color: '#E69F00' },
      ]

      const wrapper = mount(EtfBreakdownChart, {
        props: {
          title: 'Special Chars',
          chartData: specialData,
        },
      })

      const legendLabels = wrapper.findAll('.legend-label')
      expect(legendLabels[0].text()).toBe('AT&T Inc.')
      expect(legendLabels[1].text()).toBe('Johnson & Johnson')
    })
  })
})
