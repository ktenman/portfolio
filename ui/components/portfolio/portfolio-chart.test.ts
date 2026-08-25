import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { Chart, type ChartConfiguration } from 'chart.js'
import PortfolioChart from './portfolio-chart.vue'
import { CHART_COLORS } from '../../constants/chart-colors'
import { STORAGE_KEYS } from '../../constants'

vi.mock('chart.js', async importOriginal => {
  const actual = await importOriginal<typeof import('chart.js')>()
  const mockChart: any = vi.fn().mockImplementation(function (_canvas: unknown, config: any) {
    return {
      destroy: vi.fn(),
      update: vi.fn(),
      data: config.data,
      options: config.options,
    }
  })
  mockChart.register = vi.fn()
  mockChart.defaults = { font: {} }

  return { ...actual, Chart: mockChart }
})

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

  const createWrapper = async (props = {}) => {
    const wrapper = mount(PortfolioChart, {
      props: {
        data: mockChartData,
        ...props,
      },
    })
    await nextTick()
    return wrapper
  }

  const chartConfig = () =>
    vi.mocked(Chart).mock.calls[0][1] as unknown as ChartConfiguration<'line'>

  const chartData = () => chartConfig().data
  const chartOptions = (): any => chartConfig().options

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('data transformation', () => {
    it('should render chart when data is provided', async () => {
      const wrapper = await createWrapper()

      expect(wrapper.find('canvas').exists()).toBe(true)
      expect(Chart).toHaveBeenCalled()
    })

    it('should not render chart when data is null', async () => {
      const wrapper = await createWrapper({ data: null })

      expect(wrapper.find('canvas').exists()).toBe(false)
      expect(Chart).not.toHaveBeenCalled()
    })

    it('should format labels using formatDate', async () => {
      await createWrapper()

      expect(chartData().labels).toEqual(['29.12.2023', '30.12.2023', '31.12.2023'])
    })

    it('should create correct datasets structure', async () => {
      await createWrapper()
      const datasets = chartData().datasets

      expect(datasets).toHaveLength(4)
      expect(datasets[0]).toMatchObject({
        label: 'Total Value',
        borderColor: CHART_COLORS[0],
        data: mockChartData.totalValues,
        yAxisID: 'y',
      })
      expect(datasets[1]).toMatchObject({
        label: 'Total Profit',
        borderColor: CHART_COLORS[1],
        data: mockChartData.profitValues,
        yAxisID: 'y',
      })
      expect(datasets[2]).toMatchObject({
        label: 'XIRR Annual Return',
        borderColor: CHART_COLORS[3],
        data: mockChartData.xirrValues,
        yAxisID: 'y1',
      })
      expect(datasets[3]).toMatchObject({
        label: 'Earnings Per Month',
        borderColor: CHART_COLORS[5],
        data: mockChartData.earningsValues,
        yAxisID: 'y',
      })
    })

    it('should fill the area under the total value series only', async () => {
      await createWrapper()

      expect(chartData().datasets.map(dataset => dataset.fill)).toEqual([
        true,
        undefined,
        undefined,
        undefined,
      ])
    })
  })

  describe('chart configuration', () => {
    it('should pass correct options to chart', async () => {
      await createWrapper()
      const options = chartOptions()

      expect(options.responsive).toBe(true)
      expect(options.animation).toBe(false)
      expect(options.interaction.mode).toBe('index')
      expect(options.interaction.intersect).toBe(false)
    })

    it('should configure dual y-axes correctly', async () => {
      await createWrapper()
      const options = chartOptions()

      expect(options.scales.y.position).toBe('left')
      expect(options.scales.y1.position).toBe('right')
      expect(options.scales.y1.grid.drawOnChartArea).toBe(false)
    })

    it('should label the left axis with compact euro amounts', async () => {
      await createWrapper()

      expect(chartOptions().scales.y.ticks.callback(50000)).toBe('€50K')
    })

    it('should label the right axis with percentages', async () => {
      await createWrapper()

      expect(chartOptions().scales.y1.ticks.callback(12)).toBe('12%')
    })

    it('should round away floating point noise in right axis ticks', async () => {
      await createWrapper()

      expect(chartOptions().scales.y1.ticks.callback(19.700000000000003)).toBe('19.7%')
    })

    it('should place the legend below the plot with round markers', async () => {
      await createWrapper()
      const legend = chartOptions().plugins.legend

      expect(legend.position).toBe('bottom')
      expect(legend.labels.pointStyle).toBe('circle')
    })

    it('should draw horizontal gridlines only', async () => {
      await createWrapper()

      expect(chartOptions().scales.x.grid.display).toBe(false)
    })

    it('should limit ticks on axes', async () => {
      await createWrapper()
      const scales = chartOptions().scales

      expect(scales.x.ticks.maxTicksLimit).toBe(5)
      expect(scales.y.ticks.maxTicksLimit).toBe(8)
      expect(scales.y1.ticks.maxTicksLimit).toBe(8)
    })
  })

  describe('chart lifecycle', () => {
    it('should update the chart in place when the data changes', async () => {
      const wrapper = await createWrapper()
      const instance = vi.mocked(Chart).mock.results[0].value

      await wrapper.setProps({ data: { ...mockChartData, totalValues: [1, 2, 3] } })
      await nextTick()

      expect(Chart).toHaveBeenCalledTimes(1)
      expect(instance.update).toHaveBeenCalled()
      expect(instance.data.datasets[0].data).toEqual([1, 2, 3])
    })

    it('should keep legend-hidden series hidden when the data changes', async () => {
      localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, '["XIRR Annual Return"]')
      const wrapper = await createWrapper()
      const instance = vi.mocked(Chart).mock.results[0].value

      await wrapper.setProps({ data: { ...mockChartData, totalValues: [1, 2, 3] } })
      await nextTick()

      expect(instance.data.datasets[2].hidden).toBe(true)
    })

    it('should hide series stored in local storage when the chart mounts', async () => {
      localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, '["XIRR Annual Return"]')

      await createWrapper()

      expect(chartData().datasets.map(dataset => dataset.hidden)).toEqual([
        false,
        false,
        true,
        false,
      ])
    })

    it('should persist a series hidden via the legend', async () => {
      await createWrapper()
      const instance = vi.mocked(Chart).mock.results[0].value

      chartOptions().plugins.legend.onClick(null, { datasetIndex: 1 }, { chart: instance })
      await nextTick()

      expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN) ?? '[]')).toEqual([
        'Total Profit',
      ])
    })

    it('should show a hidden series again when its legend item is clicked', async () => {
      localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, '["Total Profit"]')
      await createWrapper()
      const instance = vi.mocked(Chart).mock.results[0].value

      chartOptions().plugins.legend.onClick(null, { datasetIndex: 1 }, { chart: instance })
      await nextTick()

      expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN) ?? '[]')).toEqual(
        []
      )
    })

    it('should destroy the chart when the component unmounts', async () => {
      const wrapper = await createWrapper()
      const instance = vi.mocked(Chart).mock.results[0].value

      wrapper.unmount()

      expect(instance.destroy).toHaveBeenCalled()
    })
  })

  describe('empty data handling', () => {
    it('should handle empty arrays gracefully', async () => {
      const emptyData = {
        labels: [],
        totalValues: [],
        profitValues: [],
        xirrValues: [],
        earningsValues: [],
      }

      await createWrapper({ data: emptyData })

      expect(chartData().labels).toEqual([])
      expect(chartData().datasets).toHaveLength(4)
      chartData().datasets.forEach(dataset => {
        expect(dataset.data).toEqual([])
      })
    })
  })

  describe('performance mode', () => {
    const mockPerformanceData = {
      labels: ['2023-12-29', '2023-12-30', '2023-12-31'],
      portfolioValues: [null, 0, 1.4],
      benchmarks: [{ label: 'S&P 500', color: CHART_COLORS[1], values: [null, 0, 2.1] }],
    }

    it('should render two percentage datasets in performance mode', async () => {
      await createWrapper({ data: mockPerformanceData })
      const datasets = chartData().datasets

      expect(datasets).toHaveLength(2)
      expect(datasets[0]).toMatchObject({
        label: 'Portfolio',
        borderColor: CHART_COLORS[0],
        data: mockPerformanceData.portfolioValues,
        yAxisID: 'y',
      })
      expect(datasets[1]).toMatchObject({
        label: 'S&P 500',
        borderColor: CHART_COLORS[1],
        data: mockPerformanceData.benchmarks[0].values,
        yAxisID: 'y',
      })
    })

    it('should render one dataset per selected benchmark with its own color', async () => {
      const worldValues = [null, 0, 3.4]
      await createWrapper({
        data: {
          ...mockPerformanceData,
          benchmarks: [
            ...mockPerformanceData.benchmarks,
            { label: 'World', color: CHART_COLORS[3], values: worldValues },
          ],
        },
      })
      const datasets = chartData().datasets

      expect(datasets).toHaveLength(3)
      expect(datasets[2]).toMatchObject({
        label: 'World',
        borderColor: CHART_COLORS[3],
        data: worldValues,
        yAxisID: 'y',
      })
    })

    it('should hide the right axis in performance mode', async () => {
      await createWrapper({ data: mockPerformanceData })

      expect(chartOptions().scales.y1.display).toBe(false)
    })

    it('should format the left axis as percentages in performance mode', async () => {
      await createWrapper({ data: mockPerformanceData })

      expect(chartOptions().scales.y.ticks.callback(12)).toBe('12%')
    })

    it('should format tooltips as percentages in performance mode', async () => {
      await createWrapper({ data: mockPerformanceData })
      const label = chartOptions().plugins.tooltip.callbacks.label({
        parsed: { y: 5.25 },
        dataset: { label: 'Portfolio', yAxisID: 'y' },
      })

      expect(label).toContain('%')
    })

    it('should keep the right axis visible in value mode', async () => {
      await createWrapper()

      expect(chartOptions().scales.y1.display).toBe(true)
    })

    it('should hide a stored benchmark series in performance mode', async () => {
      localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, '["S&P 500"]')

      await createWrapper({ data: mockPerformanceData })

      expect(chartData().datasets.map(dataset => dataset.hidden)).toEqual([false, true])
    })
  })
})
