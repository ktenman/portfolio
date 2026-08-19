import { onMounted, onUnmounted, watch, type Ref } from 'vue'
import Chart, { type ChartConfiguration } from 'chart.js/auto'

export function useChartLifecycle<T>(
  chartCanvas: Ref<HTMLCanvasElement | null>,
  data: Ref<T>,
  createConfig: (ctx: CanvasRenderingContext2D, data: T) => ChartConfiguration
) {
  let chartInstance: Chart | null = null

  const createChart = () => {
    if (chartInstance) {
      chartInstance.destroy()
    }
    const ctx = chartCanvas.value?.getContext('2d')
    if (!ctx) return
    const config = createConfig(ctx, data.value)
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      config.options = { ...config.options, animation: false }
    }
    chartInstance = new Chart(ctx, config)
  }

  watch(data, createChart, { deep: true })

  onMounted(() => {
    createChart()
  })

  onUnmounted(() => {
    if (chartInstance) {
      chartInstance.destroy()
    }
  })
}
