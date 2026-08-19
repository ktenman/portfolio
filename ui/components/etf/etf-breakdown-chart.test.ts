import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { Chart } from 'chart.js'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import type { ChartDataItem } from '../../services/etf-chart-service'

vi.mock('chart.js', () => {
  const mockChart: any = vi.fn().mockImplementation(function (_canvas: unknown, config: any) {
    return {
      data: config.data,
      destroy: vi.fn(),
      update: vi.fn(),
      setActiveElements: vi.fn(),
    }
  })
  mockChart.register = vi.fn()

  return {
    Chart: mockChart,
    DoughnutController: vi.fn(),
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

  const buildItems = (count: number): ChartDataItem[] =>
    Array.from({ length: count }, (_, index) => ({
      label: `Item ${index + 1}`,
      value: 10,
      percentage: '10.00',
      color: '#000000',
    }))

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('should render canvas element', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      expect(wrapper.find('canvas').exists()).toBe(true)
    })

    it('should render legend items', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(3)
    })

    it('should leave the centre of the donut empty until a slice is selected', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      expect(wrapper.find('.chart-centre').exists()).toBe(false)
    })

    it('should read out the selected slice in the centre of the donut', async () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      await wrapper.findAll('.legend-item')[1].trigger('mouseenter')

      expect(wrapper.find('.chart-centre-label').text()).toBe('Microsoft')
      expect(wrapper.find('.chart-centre-value').text()).toBe('20.30%')
    })
  })

  describe('legend content', () => {
    it('should display correct labels in legend', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
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
          chartData: [],
        },
      })

      expect(wrapper.findAll('.legend-item')).toHaveLength(0)
    })
  })

  describe('data updates', () => {
    it('should update legend when chartData changes', async () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
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

  describe('hover fill', () => {
    interface CapturedDataset {
      backgroundColor: string[]
      hoverBackgroundColor: string[]
    }

    const capturedDataset = (): CapturedDataset =>
      vi.mocked(Chart).mock.calls[0][1].data.datasets[0] as unknown as CapturedDataset

    it(`pins the hover fill to the resting fill when the chart is constructed`, () => {
      mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      const dataset = capturedDataset()
      expect(dataset.hoverBackgroundColor).toEqual(dataset.backgroundColor)
    })

    it(`keeps the hover fill array the same length as the resting fill array after the dataset shrinks`, async () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      const shorterData: ChartDataItem[] = [
        { label: 'Amazon', value: 30, percentage: '30.00', color: '#D55E00' },
      ]

      await wrapper.setProps({ chartData: shorterData })

      const dataset = capturedDataset()
      expect(dataset.hoverBackgroundColor).toHaveLength(dataset.backgroundColor.length)
    })
  })

  describe('stale hover state', () => {
    it(`clears the chart's active element when the dataset swaps after a legend hover`, async () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: mockChartData,
        },
      })

      await wrapper.findAll('.legend-item')[2].trigger('mouseenter')

      const newData: ChartDataItem[] = [
        { label: 'Amazon', value: 30, percentage: '30.00', color: '#D55E00' },
      ]
      await wrapper.setProps({ chartData: newData })

      const chartInstance = vi.mocked(Chart).mock.results[0].value
      expect(chartInstance.setActiveElements).toHaveBeenLastCalledWith([])
    })
  })

  describe('single item', () => {
    it('should render correctly with single item', () => {
      const singleItemData: ChartDataItem[] = [
        { label: 'Tesla', value: 100, percentage: '100.00', color: '#56B4E9' },
      ]

      const wrapper = mount(EtfBreakdownChart, {
        props: {
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
          chartData: manyItems,
        },
      })

      const legendItems = wrapper.findAll('.legend-item')
      expect(legendItems).toHaveLength(10)
    })

    it('should render every legend entry without a show all control', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: buildItems(16) },
      })

      expect(wrapper.findAll('.legend-item')).toHaveLength(16)
      expect(wrapper.find('.legend-toggle').exists()).toBe(false)
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
          chartData: specialData,
        },
      })

      const legendLabels = wrapper.findAll('.legend-label')
      expect(legendLabels[0].text()).toBe('AT&T Inc.')
      expect(legendLabels[1].text()).toBe('Johnson & Johnson')
    })
  })
})
