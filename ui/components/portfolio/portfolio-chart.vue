<template>
  <div class="chart-container md:w-[80.8%]" data-testid="summary-chart" v-if="chartData">
    <canvas ref="canvas"></canvas>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watchEffect } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { Chart, type ChartOptions } from 'chart.js'
import { formatDate, formatCurrencyWithSymbol } from '../../utils/formatters'
import { CHART_COLORS, withAlpha } from '../../constants/chart-colors'
import type { PerformanceChartData } from '../../composables/use-portfolio-chart'
import {
  crosshair,
  tooltipStyle,
  gridColor,
  labelColor,
  surfaceColor,
  compactAmount,
  percentAmount,
} from '../../plugins/chart'

interface ValueChartData {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

interface Props {
  data: ValueChartData | PerformanceChartData | null
}

const props = defineProps<Props>()

const isPerformance = computed(() => props.data !== null && 'benchmarkValues' in props.data)

const canvas = ref<HTMLCanvasElement | null>(null)
const isCompact = useMediaQuery('(max-width: 768px)')

let chart: Chart<'line'> | null = null

const chartData = computed(() => {
  if (!props.data) return null

  const labels = props.data.labels.map(label => formatDate(label))

  if ('benchmarkValues' in props.data) {
    return {
      labels,
      datasets: [
        {
          label: 'Portfolio',
          borderColor: CHART_COLORS[0],
          backgroundColor: CHART_COLORS[0],
          pointHoverBackgroundColor: CHART_COLORS[0],
          data: props.data.portfolioValues,
          yAxisID: 'y',
        },
        {
          label: 'S&P 500',
          borderColor: CHART_COLORS[1],
          backgroundColor: CHART_COLORS[1],
          pointHoverBackgroundColor: CHART_COLORS[1],
          data: props.data.benchmarkValues,
          yAxisID: 'y',
        },
      ],
    }
  }

  return {
    labels,
    datasets: [
      {
        label: isCompact.value ? 'Value' : 'Total Value',
        borderColor: CHART_COLORS[0],
        backgroundColor: withAlpha(CHART_COLORS[0], 0.08),
        pointHoverBackgroundColor: CHART_COLORS[0],
        fill: true,
        data: props.data.totalValues,
        yAxisID: 'y',
      },
      {
        label: isCompact.value ? 'Profit' : 'Total Profit',
        borderColor: CHART_COLORS[1],
        backgroundColor: CHART_COLORS[1],
        pointHoverBackgroundColor: CHART_COLORS[1],
        data: props.data.profitValues,
        yAxisID: 'y',
      },
      {
        label: isCompact.value ? 'XIRR' : 'XIRR Annual Return',
        borderColor: CHART_COLORS[3],
        backgroundColor: CHART_COLORS[3],
        pointHoverBackgroundColor: CHART_COLORS[3],
        data: props.data.xirrValues,
        yAxisID: 'y1',
      },
      {
        label: isCompact.value ? 'EPM' : 'Earnings Per Month',
        borderColor: CHART_COLORS[5],
        backgroundColor: CHART_COLORS[5],
        pointHoverBackgroundColor: CHART_COLORS[5],
        data: props.data.earningsValues,
        yAxisID: 'y',
      },
    ],
  }
})

const tickFont = { size: 11 }

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
          isPerformance.value
            ? `${percentAmount.format(Number(value))}%`
            : `€${compactAmount.format(Number(value))}`,
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
        callback: value => `${percentAmount.format(Number(value))}%`,
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
