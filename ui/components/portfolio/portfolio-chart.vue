<template>
  <div
    ref="root"
    class="chart-container relative md:w-[80.8%]"
    :class="{ 'touch-pan-y touch-pinch-zoom select-none [-webkit-touch-callout:none]': selectable }"
    :style="{ cursor }"
    data-testid="summary-chart"
    v-if="chartData"
  >
    <canvas ref="canvas"></canvas>
    <chart-period-layer
      v-if="selectable && frame"
      v-model:period="period"
      :frame="frame"
      :drag="drag"
      :ring="ring"
      :today="today ?? frameBounds(frame).to"
      :back-to="backLabel ?? null"
      :compact="isCompact"
      @zoom="emit('zoom')"
      @back="emit('back')"
      @legend="legendHidden = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useLocalStorage, useMediaQuery } from '@vueuse/core'
import { Chart, type ChartOptions, type LegendItem } from 'chart.js'
import { formatDate, formatDateTime, formatCurrencyWithSymbol } from '../../utils/formatters'
import { STORAGE_KEYS } from '../../constants'
import { CHART_COLORS, withAlpha } from '../../constants/chart-colors'
import type { ChartDataPoint, PerformanceChartData } from '../../composables/use-portfolio-chart'
import { usePeriodGesture } from '../../composables/use-period-gesture'
import { frameBounds, type ChartFrame } from '../../services/chart-frame'
import { MIN_PERIOD_DAYS, type ChartPeriod } from '../../services/chart-period'
import { daysBetween } from '../../utils/iso-dates'
import { createPeriodVeil } from '../../plugins/period-veil'
import ChartPeriodLayer from './chart-period-layer.vue'
import {
  crosshair,
  rangeExtremes,
  tooltipStyle,
  gridColor,
  labelColor,
  surfaceColor,
  compactAmount,
  percentAmount,
} from '../../plugins/chart'

interface Props {
  data: ChartDataPoint | PerformanceChartData | null
  today?: string
  backLabel?: string | null
}

const props = defineProps<Props>()
const period = defineModel<ChartPeriod | null>('period', { default: null })
const emit = defineEmits<{
  zoom: []
  back: []
}>()

const isPerformance = computed(() => props.data !== null && 'benchmarks' in props.data)

const valueExtremes = computed(() =>
  props.data && !('benchmarks' in props.data) ? props.data.extremes : null
)

const hiddenSeries = useLocalStorage<string[]>(STORAGE_KEYS.SUMMARY_CHART_HIDDEN, [])

const isHidden = (id: string) => hiddenSeries.value.includes(id)

const toggleSeries = (index = -1) => {
  const id = chartData.value?.datasets[index]?.seriesId
  if (!id) return
  hiddenSeries.value = isHidden(id)
    ? hiddenSeries.value.filter(existing => existing !== id)
    : [...hiddenSeries.value, id]
}

const performanceDataset = (
  seriesId: string,
  label: string,
  color: string,
  data: (number | null)[]
) => ({
  seriesId,
  label,
  hidden: isHidden(seriesId),
  borderColor: color,
  backgroundColor: color,
  pointHoverBackgroundColor: color,
  data,
  yAxisID: 'y' as const,
})

const canvas = ref<HTMLCanvasElement | null>(null)
const root = ref<HTMLElement | null>(null)
const isCompact = useMediaQuery('(max-width: 768px)')

let chart: Chart<'line'> | null = null

const dates = computed(() => props.data?.labels ?? [])

const selectable = computed(() => {
  const labels = dates.value
  if (labels.length < 2 || labels.some(label => label.includes('T'))) return false
  return daysBetween(labels[0], labels[labels.length - 1]) >= MIN_PERIOD_DAYS
})

const shownPeriod = computed(() => (selectable.value ? period.value : null))
const hasPeriod = computed(() => shownPeriod.value !== null)
const frame = ref<ChartFrame | null>(null)
const legendHidden = ref(false)
const legendVeiled = computed(() => selectable.value && legendHidden.value)

const { drag, ring, inside, cursor } = usePeriodGesture({
  root,
  frame,
  period,
  enabled: selectable,
  zoom: () => emit('zoom'),
})

const periodVeil = createPeriodVeil({
  drawing: () => ({
    period: shownPeriod.value,
    dragging: drag.value !== null,
    inside: inside.value,
  }),
  dates: () => dates.value,
  onFrame: next => {
    frame.value = next
  },
})

const veiledLabels = (target: Chart): LegendItem[] =>
  Chart.defaults.plugins.legend.labels.generateLabels(target).map(item => ({
    ...item,
    fontColor: 'transparent',
    fillStyle: 'transparent',
    strokeStyle: 'transparent',
  }))

const chartData = computed(() => {
  if (!props.data) return null

  const labels = props.data.labels.map(label =>
    label.includes('T') ? formatDateTime(label) : formatDate(label)
  )

  if ('benchmarks' in props.data) {
    return {
      labels,
      datasets: [
        performanceDataset('portfolio', 'Portfolio', CHART_COLORS[0], props.data.portfolioValues),
        ...props.data.benchmarks.map(benchmark =>
          performanceDataset(benchmark.key, benchmark.label, benchmark.color, benchmark.values)
        ),
      ],
    }
  }

  return {
    labels,
    datasets: [
      {
        seriesId: 'totalValue',
        label: isCompact.value ? 'Value' : 'Total Value',
        hidden: isHidden('totalValue'),
        borderColor: CHART_COLORS[0],
        backgroundColor: withAlpha(CHART_COLORS[0], 0.08),
        pointHoverBackgroundColor: CHART_COLORS[0],
        fill: true,
        data: props.data.totalValues,
        rangeExtremes: hasPeriod.value ? null : props.data.extremes,
        yAxisID: 'y',
      },
      {
        seriesId: 'totalProfit',
        label: isCompact.value ? 'Profit' : 'Total Profit',
        hidden: isHidden('totalProfit'),
        borderColor: CHART_COLORS[1],
        backgroundColor: CHART_COLORS[1],
        pointHoverBackgroundColor: CHART_COLORS[1],
        data: props.data.profitValues,
        rangeExtremes: hasPeriod.value ? null : props.data.profitExtremes,
        yAxisID: 'y',
      },
      {
        seriesId: 'xirrAnnualReturn',
        label: isCompact.value ? 'XIRR' : 'XIRR Annual Return',
        hidden: isHidden('xirrAnnualReturn'),
        borderColor: CHART_COLORS[3],
        backgroundColor: CHART_COLORS[3],
        pointHoverBackgroundColor: CHART_COLORS[3],
        data: props.data.xirrValues,
        yAxisID: 'y1',
      },
      {
        seriesId: 'earningsPerMonth',
        label: isCompact.value ? 'EPM' : 'Earnings Per Month',
        hidden: isHidden('earningsPerMonth'),
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
      cubicInterpolationMode: 'monotone',
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
      onClick: (_event, item) => {
        if (!legendVeiled.value) toggleSeries(item.datasetIndex)
      },
      labels: {
        usePointStyle: true,
        pointStyle: 'circle' as const,
        boxWidth: 6,
        boxHeight: 6,
        padding: 16,
        color: labelColor,
        font: tickFont,
        ...(legendVeiled.value && { generateLabels: veiledLabels }),
      },
    },
  },
  scales: {
    x: {
      border: { display: false },
      grid: { display: false, tickLength: isPerformance.value ? 8 : 25 },
      ticks: {
        maxTicksLimit: 5,
        color: hasPeriod.value ? 'transparent' : labelColor,
        font: tickFont,
      },
    },
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      grace: valueExtremes.value ? '10%' : 0,
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

watch(
  [chartData, chartOptions, canvas],
  ([data, options, element]) => {
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
    chart = new Chart(element, {
      type: 'line',
      data,
      options,
      plugins: [crosshair, rangeExtremes, periodVeil],
    })
  },
  { flush: 'post' }
)

watch([shownPeriod, drag, inside], () => chart?.draw())

onBeforeUnmount(() => chart?.destroy())
</script>
