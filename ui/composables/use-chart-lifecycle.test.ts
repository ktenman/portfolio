import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import Chart, { type ChartConfiguration } from 'chart.js/auto'
import { useChartLifecycle } from './use-chart-lifecycle'

vi.mock('chart.js/auto', () => ({
  default: vi.fn().mockImplementation(function () {
    return { destroy: vi.fn() }
  }),
}))

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

const chartOptions = () =>
  (vi.mocked(Chart).mock.calls[0][1] as unknown as ChartConfiguration).options

const emulateReducedMotion = (matches: boolean) => {
  window.matchMedia = vi.fn().mockReturnValue({ matches }) as unknown as typeof window.matchMedia
}

describe('useChartLifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({})
  })

  it('should turn animation off when the reader asks for reduced motion', () => {
    emulateReducedMotion(true)

    mount(host)

    expect(chartOptions()).toMatchObject({ responsive: true, animation: false })
  })

  it('should leave the configured animation alone otherwise', () => {
    emulateReducedMotion(false)

    mount(host)

    expect(chartOptions()).toEqual({ responsive: true })
  })
})
