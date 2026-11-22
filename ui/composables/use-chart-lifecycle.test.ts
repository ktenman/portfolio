import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import Chart, { type ChartConfiguration } from 'chart.js/auto'
import { useChartLifecycle } from './use-chart-lifecycle'
import { useTheme } from './use-theme'

vi.mock('chart.js/auto', () => ({
  default: vi.fn().mockImplementation(function () {
    return { destroy: vi.fn() }
  }),
}))

vi.mock('../plugins/chart', () => ({
  chartTheme: { value: { label: 'ink-soft värv', grid: 'hairline värv' } },
}))

enableAutoUnmount(afterEach)

const host = defineComponent({
  setup() {
    const canvas = ref<HTMLCanvasElement | null>(null)
    useChartLifecycle(canvas, ref([1, 2, 3]), () => ({
      type: 'bar',
      data: { labels: [], datasets: [] },
      options: { responsive: true },
    }))
    return () => h('canvas', { ref: canvas })
  },
})

const DARK_AXIS = {
  ticks: { color: 'ink-soft värv' },
  title: { color: 'ink-soft värv' },
  grid: { color: 'hairline värv' },
  border: { color: 'hairline värv' },
}

const chartOptions = (call = 0) =>
  (vi.mocked(Chart).mock.calls[call][1] as unknown as ChartConfiguration).options

describe('useChartLifecycle', () => {
  beforeEach(() => {
    useTheme().theme.value = 'light'
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({})
    Object.assign(document.fonts, { ready: Promise.resolve() })
  })

  it('should turn animation off when the reader asks for reduced motion', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))

    mount(host)
    await flushPromises()

    expect(chartOptions()).toMatchObject({ responsive: true, animation: false })
  })

  it('should leave the configured animation alone otherwise', async () => {
    mount(host)
    await flushPromises()

    expect(chartOptions()).toEqual({ aspectRatio: 2, responsive: true })
  })

  it('should redraw in the dark scheme with its label and grid colours when the scheme turns dark', async () => {
    mount(host)
    await flushPromises()
    useTheme().theme.value = 'dark'
    await flushPromises()

    expect([vi.mocked(Chart).mock.calls.length, chartOptions(0), chartOptions(1)]).toEqual([
      2,
      { aspectRatio: 2, responsive: true },
      {
        aspectRatio: 2,
        responsive: true,
        color: 'ink-soft värv',
        borderColor: 'hairline värv',
        scales: { x: DARK_AXIS, y: DARK_AXIS },
        plugins: { title: { color: 'ink-soft värv' } },
      },
    ])
  })

  it('should build the chart only once the page fonts have loaded', async () => {
    let loaded = () => {}
    Object.assign(document.fonts, { ready: new Promise<void>(resolve => (loaded = resolve)) })
    mount(host)
    await flushPromises()
    const before = vi.mocked(Chart).mock.calls.length
    loaded()
    await flushPromises()

    expect([before, vi.mocked(Chart).mock.calls.length]).toEqual([0, 1])
  })
})
