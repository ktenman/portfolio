<template>
  <canvas ref="chartCanvas"></canvas>
</template>

<script setup lang="ts">
import { ref, toRef } from 'vue'
import { useChartLifecycle } from '../../composables/use-chart-lifecycle'
import { crosshair, tooltipStyle } from '../../plugins/chart'
import { formatCurrency, formatCurrencyWithSymbol } from '../../utils/formatters'
import { CHART_COLORS, withAlpha } from '../../constants/chart-colors'

interface ChartProps {
  data: number[]
  title?: string
  xAxisLabel?: string
  yAxisLabel?: string
  borderColor?: string
  backgroundColor?: string
}

const props = withDefaults(defineProps<ChartProps>(), {
  title: 'Line Chart',
  xAxisLabel: 'X Axis',
  yAxisLabel: 'Y Axis',
  borderColor: CHART_COLORS[0],
  backgroundColor: withAlpha(CHART_COLORS[0], 0.12),
})

const chartCanvas = ref<HTMLCanvasElement | null>(null)

useChartLifecycle(chartCanvas, toRef(props, 'data'), () => ({
  type: 'line',
  plugins: [crosshair],
  data: {
    labels: Array.from({ length: props.data.length }, (_, i) => i + 1),
    datasets: [
      {
        label: props.yAxisLabel,
        data: props.data,
        borderColor: props.borderColor,
        backgroundColor: props.backgroundColor,
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 4,
        pointHitRadius: 10,
        fill: false,
      },
    ],
  },
  options: {
    responsive: true,
    interaction: {
      mode: 'index',
      intersect: false,
    },
    scales: {
      x: {
        title: { display: true, text: props.xAxisLabel },
        grid: { display: false },
      },
      y: {
        title: { display: true, text: props.yAxisLabel },
        ticks: {
          callback: value =>
            props.yAxisLabel.includes('€') ? formatCurrency(value as number) : value,
        },
      },
    },
    plugins: {
      title: { display: true, text: props.title, font: { size: 16 } },
      legend: { display: false },
      tooltip: {
        ...tooltipStyle,
        callbacks: {
          title: items => `${props.xAxisLabel} ${items[0].label}`,
          label: context =>
            ` ${props.yAxisLabel.includes('€') ? formatCurrencyWithSymbol(context.parsed.y) : context.parsed.y}`,
        },
      },
    },
  },
}))
</script>
