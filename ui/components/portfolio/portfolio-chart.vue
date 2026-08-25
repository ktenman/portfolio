<template>
  <div class="chart-container md:w-[80.8%]" data-testid="summary-chart" v-if="chartData">
    <canvas ref="canvas"></canvas>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watchEffect } from 'vue'
import { useLocalStorage, useMediaQuery } from '@vueuse/core'
import { Chart, type ChartOptions } from 'chart.js'
import { formatDate, formatCurrencyWithSymbol } from '../../utils/formatters'
import { STORAGE_KEYS } from '../../constants'
import { CHART_COLORS, withAlpha } from '../../constants/chart-colors'
import type { ChartDataPoint, PerformanceChartData } from '../../composables/use-portfolio-chart'
import {
  crosshair,
  tooltipStyle,
  gridColor,
  labelColor,
  surfaceColor,
  compactAmount,
  percentAmount,
} from '../../plugins/chart'

interface Props {
  data: ChartDataPoint | PerformanceChartData | null
}

const props = defineProps<Props>()

const isPerformance = computed(() => props.data !== null && 'benchmarks' in props.data)

const hiddenSeries = useLocalStorage<string[]>(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, [])

const isHidden = (id: string) => hiddenSeries.value.includes(id)

const toggleSeries = (id: string | undefined) => {
  if (!id) return
  hiddenSeries.value = isHidden(id)
    ? hiddenSeries.value.filter(existing => existing !== id)
    : [...hiddenSeries.value, id]
}

const withHidden = <T extends { seriesId: string }>(dataset: T) => ({
  ...dataset,
  hidden: isHidden(dataset.seriesId),
})

const performanceDataset = (label: string, color: string, data: (number | null)[]) => ({
  label,
  seriesId: label,
  borderColor: color,
  backgroundColor: color,
  pointHoverBackgroundColor: color,
  data,
  yAxisID: 'y' as const,
})

const canvas = ref<HTMLCanvasElement | null>(null)
const isCompact = useMediaQuery('(max-width: 768px)')

let chart: Chart<'line'> | null = null

const chartData = computed(() => {
  if (!props.data) return null

  const labels = props.data.labels.map(label => formatDate(label))

  if ('benchmarks' in props.data) {
    return {
      labels,
      datasets: [
        performanceDataset('Portfolio', CHART_COLORS[0], props.data.portfolioValues),
        ...props.data.benchmarks.map(benchmark =>
          performanceDataset(benchmark.label, benchmark.color, benchmark.values)
        ),
      ].map(withHidden),
    }
  }

  return {
    labels,
    datasets: [
      {
        seriesId: 'Total Value',
        label: isCompact.value ? 'Value' : 'Total Value',
        borderColor: CHART_COLORS[0],
        backgroundColor: withAlpha(CHART_COLORS[0], 0.08),
        pointHoverBackgroundColor: CHART_COLORS[0],
        fill: true,
        data: props.data.totalValues,
        yAxisID: 'y',
      },
      {
        seriesId: 'Total Profit',
        label: isCompact.value ? 'Profit' : 'Total Profit',
        borderColor: CHART_COLORS[1],
        backgroundColor: CHART_COLORS[1],
        pointHoverBackgroundColor: CHART_COLORS[1],
        data: props.data.profitValues,
        yAxisID: 'y',
      },
      {
        seriesId: 'XIRR Annual Return',
        label: isCompact.value ? 'XIRR' : 'XIRR Annual Return',
        borderColor: CHART_COLORS[3],
        backgroundColor: CHART_COLORS[3],
        pointHoverBackgroundColor: CHART_COLORS[3],
        data: props.data.xirrValues,
        yAxisID: 'y1',
      },
      {
        seriesId: 'Earnings Per Month',
        label: isCompact.value ? 'EPM' : 'Earnings Per Month',
        borderColor: CHART_COLORS[5],
        backgroundColor: CHART_COLORS[5],
        pointHoverBackgroundColor: CHART_COLORS[5],
        data: props.data.earningsValues,
        yAxisID: 'y',
      },
    ].map(withHidden),
  }
})

const tickFont = { size: 11 }

const percentTick = (value: number | string) => `${percentAmount.format(Number(value))}%`

const chartOptions = computed<ChartOptions<'line'>>(() => ({
  responsive: true,
  aspectRatio: isCompact.value ? 1.76 : 2.05,
  animation: false,
  interaction: {
    mode: 'index',
    intersect: false,
  },
  layout: {
    padding: { top: 28 },
  },
  elements: {
    point: {
      radius: 0,
      hoverRadius: 5,
      hoverBorderWidth: 2,
      hoverBorderColor: surfaceColor,
    },
    line: {
      tension: 0.4,
      borderWidth: 2,
    },
  },
  plugins: {
    tooltip: {
      ...tooltipStyle,
      callbacks: {
        label: context => {
          const value = context.parsed.y
          if (value === null) return ` ${context.dataset.label}`
          const formatted =
            isPerformance.value || context.dataset.yAxisID === 'y1'
              ? `${value.toFixed(2)}%`
              : formatCurrencyWithSymbol(value)
          return ` ${context.dataset.label}  ${formatted}`
        },
      },
    },
    legend: {
      position: 'bottom' as const,
      align: 'start' as const,
      onClick: (_event, item) =>
        toggleSeries(chartData.value?.datasets[item.datasetIndex ?? -1]?.seriesId),
      labels: {
        usePointStyle: true,
        pointStyle: 'circle' as const,
        boxWidth: 6,
        boxHeight: 6,
        padding: 16,
        color: labelColor,
        font: tickFont,
      },
    },
  },
  scales: {
    x: {
      border: { display: false },
      grid: { display: false },
      ticks: {
        maxTicksLimit: 5,
        color: labelColor,
        font: tickFont,
      },
    },
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      border: { display: false },
      grid: { color: gridColor },
      ticks: {
        maxTicksLimit: 8,
        color: labelColor,
        font: tickFont,
        callback: value =>
          isPerformance.value ? percentTick(value) : `€${compactAmount.format(Number(value))}`,
      },
    },
    y1: {
      display: !isPerformance.value,
      type: 'linear' as const,
      position: 'right' as const,
      border: { display: false },
      grid: {
        drawOnChartArea: false,
      },
      ticks: {
        maxTicksLimit: 8,
        color: labelColor,
        font: tickFont,
        callback: percentTick,
      },
    },
  },
}))

watchEffect(
  () => {
    const data = chartData.value
    const options = chartOptions.value
    const element = canvas.value
    if (!element || !data) {
      chart?.destroy()
      chart = null
      return
    }
    if (chart) {
      chart.data = data
      chart.options = options
      chart.update('none')
      return
    }
    chart = new Chart(element, { type: 'line', data, options, plugins: [crosshair] })
  },
  { flush: 'post' }
)

onBeforeUnmount(() => chart?.destroy())
</script>
