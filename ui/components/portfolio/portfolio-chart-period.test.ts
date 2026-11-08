import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { Chart, type ChartConfiguration, type Plugin } from 'chart.js'
import PortfolioChart from './portfolio-chart.vue'
import ChartPeriodLayer from './chart-period-layer.vue'
import type { ChartDataPoint } from '../../composables/use-portfolio-chart'
import { fakeChart } from '../../tests/fixtures'

vi.mock('chart.js', async importOriginal => {
  const actual = await importOriginal<typeof import('chart.js')>()
  const mockChart: any = vi.fn().mockImplementation(function () {
    return { destroy: vi.fn(), update: vi.fn(), draw: vi.fn(), data: null, options: null }
  })
  mockChart.register = vi.fn()
  mockChart.defaults = {
    font: {},
    plugins: {
      legend: {
        labels: {
          generateLabels: () => [
            { text: 'Total Value', fontColor: 'black', fillStyle: 'blue', strokeStyle: 'blue' },
          ],
        },
      },
    },
  }
  return { ...actual, Chart: mockChart }
})

enableAutoUnmount(afterEach)

const DAYS: ChartDataPoint = {
  labels: ['2026-01-01', '2026-01-11', '2026-01-21'],
  totalValues: [1000, 1100, 1250],
  profitValues: [100, 110, 125],
  xirrValues: [4, 5, 6],
  earningsValues: [10, 11, 12],
  extremes: { low: 0, high: 2 },
  profitExtremes: { low: 0, high: 2 },
}

const HOURS: ChartDataPoint = {
  ...DAYS,
  labels: ['2026-01-21T09:00:00', '2026-01-21T12:00:00', '2026-01-21T15:00:00'],
}

const PERIOD = { from: '2026-01-03', to: '2026-01-11' }

const FAKE_CHART = fakeChart()

const chartConfig = () => vi.mocked(Chart).mock.calls[0][1] as unknown as ChartConfiguration<'line'>

const chartInstance = () => vi.mocked(Chart).mock.results[0].value

const datasets = () =>
  chartConfig().data.datasets as unknown as { rangeExtremes?: unknown; hidden: boolean }[]

const veil = () =>
  (chartConfig().plugins ?? []).find(plugin => plugin.id === 'periodVeil') as Plugin<'line'>

const mountChart = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(PortfolioChart, {
    props: { data: DAYS, ...props },
    attachTo: document.body,
  })
  await nextTick()
  return wrapper
}

const showFrame = async () => {
  veil().afterRender?.(FAKE_CHART, {}, {})
  await nextTick()
}

const mouseDrag = (target: Element, from: number, to: number) => {
  const press = { bubbles: true, clientY: 100, pointerId: 1, pointerType: 'mouse', button: 0 }
  target.dispatchEvent(new PointerEvent('pointerdown', { ...press, clientX: from }))
  target.dispatchEvent(new PointerEvent('pointermove', { ...press, buttons: 1, clientX: to }))
  target.dispatchEvent(new PointerEvent('pointerup', { ...press, clientX: to }))
}

const giveWayToLayer = async (wrapper: VueWrapper) => {
  wrapper.findComponent(ChartPeriodLayer).vm.$emit('legend', true)
  await nextTick()
}

describe('PortfolioChart period selection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('should hand the period veil to the chart', async () => {
    await mountChart()

    expect(veil()).toBeDefined()
  })

  it('should hide the dates under the plot while a period is drawn', async () => {
    await mountChart({ period: PERIOD })

    expect((chartConfig().options?.scales?.x?.ticks as { color: string }).color).toBe('transparent')
  })

  it('should hide the high and low marks while a period is drawn', async () => {
    await mountChart({ period: PERIOD })

    expect(datasets().some(dataset => dataset.rangeExtremes)).toBe(false)
  })

  it('should lay the period layer over a chart of whole days', async () => {
    const wrapper = await mountChart()
    await showFrame()

    expect(wrapper.findComponent(ChartPeriodLayer).exists()).toBe(true)
  })

  it('should leave an intraday chart without the period layer', async () => {
    const wrapper = await mountChart({ data: HOURS })
    await showFrame()

    expect(wrapper.findComponent(ChartPeriodLayer).exists()).toBe(false)
  })

  it('should leave a view shorter than two days without the period layer', async () => {
    const wrapper = await mountChart({ data: { ...DAYS, labels: ['2026-01-20', '2026-01-21'] } })
    await showFrame()

    expect(wrapper.findComponent(ChartPeriodLayer).exists()).toBe(false)
  })

  it('should draw a period across a drag over whole days', async () => {
    const wrapper = await mountChart()
    await showFrame()
    mouseDrag(wrapper.element, 72, 200)

    expect(wrapper.emitted('update:period')?.[0]).toEqual([PERIOD])
  })

  it('should let a pinch zoom the page from the chart', async () => {
    const wrapper = await mountChart()

    expect(wrapper.get('[data-testid="summary-chart"]').classes()).toContain('touch-pinch-zoom')
  })

  it('should ignore a drag across an intraday chart', async () => {
    const wrapper = await mountChart({ data: HOURS })
    await showFrame()
    mouseDrag(wrapper.element, 72, 200)

    expect(wrapper.emitted('update:period')).toBeUndefined()
  })

  it('should redraw the chart when the period moves', async () => {
    const wrapper = await mountChart({ period: PERIOD })
    await wrapper.setProps({ period: { from: '2026-01-05', to: '2026-01-15' } })

    expect(chartInstance().draw).toHaveBeenCalled()
  })

  it('should pass a cleared period up to the page', async () => {
    const wrapper = await mountChart({ period: PERIOD })
    await showFrame()
    await wrapper.get('[data-testid="period-clear"]').trigger('click')

    expect(wrapper.emitted('update:period')?.[0]).toEqual([null])
  })

  it('should pass a zoom from the period layer up to the page', async () => {
    const wrapper = await mountChart({ period: PERIOD })
    await showFrame()
    await wrapper.get('[data-testid="period-zoom"]').trigger('click')

    expect(wrapper.emitted('zoom')).toHaveLength(1)
  })

  it('should pass the way back from a zoom up to the page', async () => {
    const wrapper = await mountChart({ backLabel: '1Y' })
    await showFrame()
    await wrapper.get('[data-testid="period-back"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('should make the legend transparent while it gives way to the period', async () => {
    const wrapper = await mountChart({ period: PERIOD })
    await showFrame()
    await giveWayToLayer(wrapper)
    const labels = chartInstance().options.plugins.legend.labels.generateLabels(FAKE_CHART)

    expect(labels[0]).toMatchObject({
      fontColor: 'transparent',
      fillStyle: 'transparent',
      strokeStyle: 'transparent',
    })
  })

  it('should not hide a series from a legend that gives way to the period', async () => {
    const wrapper = await mountChart({ period: PERIOD })
    await showFrame()
    await giveWayToLayer(wrapper)
    chartInstance().options.plugins.legend.onClick(null, { datasetIndex: 0 })
    await nextTick()

    expect(chartInstance().data.datasets[0].hidden).toBe(false)
  })
})
