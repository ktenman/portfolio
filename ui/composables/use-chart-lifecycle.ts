import { onMounted, onUnmounted, watch, type Ref } from 'vue'
import Chart, { type ChartConfiguration } from 'chart.js/auto'
import { merge } from 'chart.js/helpers'
import { chartTheme } from '../plugins/chart'
import { useTheme } from './use-theme'

export function useChartLifecycle<T>(
  chartCanvas: Ref<HTMLCanvasElement | null>,
  data: Ref<T>,
  createConfig: (ctx: CanvasRenderingContext2D, data: T) => ChartConfiguration
) {
  let chartInstance: Chart | null = null
  const { dark } = useTheme()

  const createChart = async () => {
    await document.fonts.ready
    if (chartInstance) {
      chartInstance.destroy()
    }
    const ctx = chartCanvas.value?.getContext('2d')
    if (!ctx) return
    const config = createConfig(ctx, data.value)
    config.options = { aspectRatio: 2, ...config.options }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      config.options = { ...config.options, animation: false }
    }
    if (dark.value) {
      const { label, grid } = chartTheme.value
      const axis = {
        ticks: { color: label },
        title: { color: label },
        grid: { color: grid },
        border: { color: grid },
      }
      config.options = merge(config.options, {
        color: label,
        borderColor: grid,
        scales: { x: axis, y: axis },
        plugins: { title: { color: label } },
      })
    }
    chartInstance = new Chart(ctx, config)
  }

  watch(data, createChart, { deep: true })
  watch(dark, createChart)

  onMounted(() => {
    createChart()
  })

  onUnmounted(() => {
    if (chartInstance) {
      chartInstance.destroy()
    }
  })
}
