<template>
  <canvas ref="chartCanvas"></canvas>
</template>

<script setup lang="ts">
import { ref, toRef } from 'vue'
import { useChartLifecycle } from '../../composables/use-chart-lifecycle'
import { formatCurrency } from '../../utils/formatters'

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
  borderColor: 'rgba(75, 192, 192, 1)',
  backgroundColor: 'rgba(75, 192, 192, 0.2)',
})

const chartCanvas = ref<HTMLCanvasElement | null>(null)

useChartLifecycle(chartCanvas, toRef(props, 'data'), () => ({
  type: 'line',
  data: {
    labels: Array.from({ length: props.data.length }, (_, i) => i + 1),
    datasets: [
      {
        label: props.yAxisLabel,
        data: props.data,
        borderColor: props.borderColor,
        backgroundColor: props.backgroundColor,
        borderWidth: 2,
        fill: false,
      },
    ],
  },
  options: {
    responsive: true,
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
    },
  },
}))
</script>
