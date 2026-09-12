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
    { label: 'Apple', value: 25.5, color: '#0072B2', isOther: false },
    { label: 'Microsoft', value: 20.3, color: '#E69F00', isOther: false },
    { label: 'Google', value: 15.2, color: '#009E73', isOther: false },
  ]

  const comparedItem: ChartDataItem = {
    label: 'Banks',
    value: 9.2,
    isOther: false,
    color: '#0072B2',
    benchmark: 4,
    ratio: 2.3,
  }

  const buildItems = (count: number): ChartDataItem[] =>
    Array.from({ length: count }, (_, index) => ({
      label: `Item ${index + 1}`,
      value: 10,
      isOther: false,
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

    it('should show the benchmark share and ratio under a compared item', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: [comparedItem] },
      })

      expect(wrapper.find('.legend-benchmark').text()).toBe('4.00% · 2.30×')
    })

    it('should clear the focused slice when its legend item is tapped again', async () => {
      const wrapper = mount(EtfBreakdownChart, { props: { chartData: mockChartData } })
      const item = wrapper.findAll('.legend-item')[1]
      await item.trigger('click')
      await item.trigger('click')
      expect(wrapper.findAll('.legend-item.dimmed')).toHaveLength(0)
    })

    it('should focus a slice when its legend item is tapped', async () => {
      const wrapper = mount(EtfBreakdownChart, { props: { chartData: mockChartData } })
      await wrapper.findAll('.legend-item')[1].trigger('click')
      expect(wrapper.findAll('.legend-item.dimmed')).toHaveLength(2)
    })

    it('should flag a ratio above 2', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: [comparedItem] },
      })

      expect(wrapper.find('.legend-benchmark').classes()).toContain('flagged')
    })

    it('should flag a ratio below 0.5', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: [{ ...comparedItem, benchmark: 46, ratio: 0.2 }],
        },
      })

      expect(wrapper.find('.legend-benchmark').classes()).toContain('flagged')
    })

    it('should not flag a ratio of exactly 2', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: [{ ...comparedItem, benchmark: 4.6, ratio: 2 }] },
      })

      expect(wrapper.find('.legend-benchmark').classes()).not.toContain('flagged')
    })

    it('should not flag a ratio of exactly 0.5', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: [{ ...comparedItem, benchmark: 18.4, ratio: 0.5 }] },
      })

      expect(wrapper.find('.legend-benchmark').classes()).not.toContain('flagged')
    })

    it('should not flag a ratio inside the half-to-double band', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: [{ ...comparedItem, benchmark: 7.36, ratio: 1.25 }],
        },
      })

      expect(wrapper.find('.legend-benchmark').classes()).not.toContain('flagged')
    })

    it('should omit the ratio when the benchmark has no weight', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: {
          chartData: [{ ...comparedItem, benchmark: 0, ratio: undefined }],
        },
      })

      expect(wrapper.find('.legend-benchmark').text()).toBe('0.00%')
    })

    it('should not render a benchmark line for items without a benchmark', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: mockChartData },
      })

      expect(wrapper.find('.legend-benchmark').exists()).toBe(false)
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
        { label: 'Amazon', value: 30, color: '#D55E00', isOther: false },
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
        { label: 'Amazon', value: 30, color: '#D55E00', isOther: false },
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
        { label: 'Amazon', value: 30, color: '#D55E00', isOther: false },
      ]
      await wrapper.setProps({ chartData: newData })

      const chartInstance = vi.mocked(Chart).mock.results[0].value
      expect(chartInstance.setActiveElements).toHaveBeenLastCalledWith([])
    })
  })

  describe('single item', () => {
    it('should render correctly with single item', () => {
      const singleItemData: ChartDataItem[] = [
        { label: 'Tesla', value: 100, color: '#56B4E9', isOther: false },
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
        isOther: false,
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
        { label: 'AT&T Inc.', value: 25, color: '#0072B2', isOther: false },
        {
          label: 'Johnson & Johnson',
          value: 30,
          color: '#E69F00',
          isOther: false,
        },
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

  describe('bars view', () => {
    const mountBars = () =>
      mount(EtfBreakdownChart, { props: { chartData: mockChartData, view: 'bars' } })

    it('replaces the donut with ranked bars', () => {
      const wrapper = mountBars()
      expect([wrapper.find('canvas').exists(), wrapper.findAll('.breakdown-row').length]).toEqual([
        false,
        3,
      ])
    })

    it('drops the legend grid while the bars are shown', () => {
      expect(mountBars().find('.chart-legend').exists()).toBe(false)
    })

    it('paints each bar with the colour of its donut slice', () => {
      expect(mountBars().find('.row-bar').attributes('style')).toContain(
        `--row-bar-color: ${mockChartData[0].color}`
      )
    })

    it('names the benchmark in the title of a compared bar', () => {
      const wrapper = mount(EtfBreakdownChart, {
        props: { chartData: [comparedItem], view: 'bars', benchmarkLabel: 'VWCE' },
      })
      expect(wrapper.find('.breakdown-row').attributes('title')).toBe('Banks 9.20% · VWCE 4.00%')
    })

    it('keeps the donut when no view is given', () => {
      const wrapper = mount(EtfBreakdownChart, { props: { chartData: mockChartData } })
      expect(wrapper.find('canvas').exists()).toBe(true)
    })
  })
})
