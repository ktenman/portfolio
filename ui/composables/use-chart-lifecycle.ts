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
    chartInstance = new Chart(ctx, createConfig(ctx, data.value))
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
