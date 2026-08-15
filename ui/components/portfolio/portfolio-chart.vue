<template>
  <div class="chart-container md:w-[80.8%]" data-testid="summary-chart" v-if="chartData">
    <Line :data="chartData" :options="chartOptions" :plugins="[crosshair]" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { Line } from 'vue-chartjs'
import type { ChartOptions } from 'chart.js'
import { formatDate, formatCurrencyWithSymbol } from '../../utils/formatters'
import { CHART_COLORS, withAlpha } from '../../constants/chart-colors'
import {
  crosshair,
  tooltipStyle,
  gridColor,
  labelColor,
  surfaceColor,
  compactAmount,
} from '../../plugins/chart'

interface Props {
  data: {
    labels: string[]
    totalValues: number[]
    profitValues: number[]
    xirrValues: number[]
    earningsValues: number[]
  } | null
}

const props = defineProps<Props>()

const isCompact = useMediaQuery('(max-width: 768px)')

const chartData = computed(() => {
  if (!props.data) return null

  return {
    labels: props.data.labels.map(label => formatDate(label)),
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
  aspectRatio: isCompact.value ? 1.85 : 2.15,
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
            context.dataset.yAxisID === 'y1'
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
        callback: value => `€${compactAmount.format(Number(value))}`,
      },
    },
    y1: {
      type: 'linear' as const,
      display: true,
      position: 'right' as const,
      border: { display: false },
      grid: {
        drawOnChartArea: false,
      },
      ticks: {
        maxTicksLimit: 8,
        color: labelColor,
        font: tickFont,
        callback: value => `${value}%`,
      },
    },
  },
}))
</script>
