import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortfolioChart from './portfolio-chart.vue'
import { CHART_COLORS } from '../../constants/chart-colors'

vi.mock('vue-chartjs', () => ({
  Line: {
    name: 'Line',
    props: ['data', 'options'],
    template: '<div class="mock-chart">{{ JSON.stringify(data) }}</div>',
  },
}))

vi.mock('../../utils/formatters', () => ({
  formatDate: vi.fn((date: string) => {
    const d = new Date(date)
    return `${d.getDate()}.${d.getMonth() + 1}.${d.getFullYear()}`
  }),
}))

describe('PortfolioChart', () => {
  const mockChartData = {
    labels: ['2023-12-29', '2023-12-30', '2023-12-31'],
    totalValues: [45000, 47500, 50000],
    profitValues: [3000, 4000, 5000],
    xirrValues: [10.5, 11.2, 12.0],
    earningsValues: [2500, 2750, 3000],
  }

  const createWrapper = (props = {}) => {
    return mount(PortfolioChart, {
      props: {
        data: mockChartData,
        ...props,
      },
    })
  }

  describe('data transformation', () => {
    it('should render chart when data is provided', () => {
      const wrapper = createWrapper()
      const chart = wrapper.find('.mock-chart')
      expect(chart.exists()).toBe(true)
    })

    it('should not render chart when data is null', () => {
      const wrapper = createWrapper({ data: null })
      const chart = wrapper.find('.mock-chart')
      expect(chart.exists()).toBe(false)
    })

    it('should format labels using formatDate', () => {
      const wrapper = createWrapper()
      const chartData = JSON.parse(wrapper.find('.mock-chart').text())

      expect(chartData.labels).toEqual(['29.12.2023', '30.12.2023', '31.12.2023'])
    })

    it('should create correct datasets structure', () => {
      const wrapper = createWrapper()
      const chartData = JSON.parse(wrapper.find('.mock-chart').text())

      expect(chartData.datasets).toHaveLength(4)
      expect(chartData.datasets[0]).toMatchObject({
        label: 'Total Value',
        borderColor: CHART_COLORS[0],
        data: mockChartData.totalValues,
        yAxisID: 'y',
      })
      expect(chartData.datasets[1]).toMatchObject({
        label: 'Total Profit',
        borderColor: CHART_COLORS[1],
        data: mockChartData.profitValues,
        yAxisID: 'y',
      })
      expect(chartData.datasets[2]).toMatchObject({
        label: 'XIRR Annual Return',
        borderColor: CHART_COLORS[3],
        data: mockChartData.xirrValues,
        yAxisID: 'y1',
      })
      expect(chartData.datasets[3]).toMatchObject({
        label: 'Earnings Per Month',
        borderColor: CHART_COLORS[5],
        data: mockChartData.earningsValues,
        yAxisID: 'y',
      })
    })

    it('should fill the area under the total value series only', () => {
      const wrapper = createWrapper()
      const chartData = JSON.parse(wrapper.find('.mock-chart').text())

      expect(chartData.datasets.map((dataset: { fill?: boolean }) => dataset.fill)).toEqual([
        true,
        undefined,
        undefined,
        undefined,
      ])
    })
  })

  describe('chart configuration', () => {
    it('should pass correct options to chart', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.responsive).toBe(true)
      expect(options.animation).toBe(false)
      expect(options.interaction.mode).toBe('index')
      expect(options.interaction.intersect).toBe(false)
    })

    it('should configure dual y-axes correctly', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.scales.y.position).toBe('left')
      expect(options.scales.y1.position).toBe('right')
      expect(options.scales.y1.grid.drawOnChartArea).toBe(false)
    })

    it('should label the left axis with compact euro amounts', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.scales.y.ticks.callback(50000)).toBe('€50K')
    })

    it('should label the right axis with percentages', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.scales.y1.ticks.callback(12)).toBe('12%')
    })

    it('should place the legend below the plot with round markers', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.plugins.legend.position).toBe('bottom')
      expect(options.plugins.legend.labels.pointStyle).toBe('circle')
    })

    it('should draw horizontal gridlines only', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.scales.x.grid.display).toBe(false)
    })

    it('should limit ticks on axes', () => {
      const wrapper = createWrapper()
      const chart = wrapper.findComponent({ name: 'Line' })
      const options = chart.props('options')

      expect(options.scales.x.ticks.maxTicksLimit).toBe(5)
      expect(options.scales.y.ticks.maxTicksLimit).toBe(8)
      expect(options.scales.y1.ticks.maxTicksLimit).toBe(8)
    })
  })

  describe('empty data handling', () => {
    it('should handle empty arrays gracefully', () => {
      const emptyData = {
        labels: [],
        totalValues: [],
        profitValues: [],
        xirrValues: [],
        earningsValues: [],
      }

      const wrapper = createWrapper({ data: emptyData })
      const chartData = JSON.parse(wrapper.find('.mock-chart').text())

      expect(chartData.labels).toEqual([])
      expect(chartData.datasets).toHaveLength(4)
      chartData.datasets.forEach((dataset: any) => {
        expect(dataset.data).toEqual([])
      })
    })
  })
})
