<template>
  <canvas ref="chartCanvas"></canvas>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import Chart from 'chart.js/auto'

interface ChartDataPoint {
  date: string
  amount: number
}

interface ChartProps {
  data: ChartDataPoint[]
  title?: string
  xAxisLabel?: string
  yAxisLabel?: string
  backgroundColor?: string
  borderColor?: string
  maxPoints?: number
}

const props = withDefaults(defineProps<ChartProps>(), {
  title: '',
  xAxisLabel: 'Date',
  yAxisLabel: 'Value',
  backgroundColor: 'rgba(75, 192, 192, 0.6)',
  borderColor: 'rgba(75, 192, 192, 1)',
  maxPoints: 50,
})

const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chartInstance: Chart | null = null

const applyASAP = (data: ChartDataPoint[], maxPoints: number): ChartDataPoint[] => {
  if (data.length <= maxPoints) return data

  const step = Math.floor(data.length / maxPoints)
  const result = []

  for (let i = 0; i < data.length; i += step) {
    const chunk = data.slice(i, Math.min(i + step, data.length))
    const avgAmount = chunk.reduce((sum, item) => sum + item.amount, 0) / chunk.length
    result.push({ date: chunk[0].date, amount: avgAmount })
  }

  if (result[result.length - 1].date !== data[data.length - 1].date) {
    result.push(data[data.length - 1])
  }

  return result
}

const createChart = () => {
  if (chartInstance) {
    chartInstance.destroy()
  }

  const ctx = chartCanvas.value?.getContext('2d')
  if (!ctx) return

  const maxPoints = Math.max(Math.floor(ctx.canvas.width / 15), 26)
  const processedData = applyASAP(props.data, Math.min(maxPoints, props.maxPoints))

  chartInstance = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: processedData.map(x => x.date),
      datasets: [
        {
          label: props.yAxisLabel,
          data: processedData.map(x => x.amount),
          backgroundColor: props.backgroundColor,
          borderColor: props.borderColor,
          borderWidth: 1,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          title: { display: true, text: props.xAxisLabel },
          grid: { display: false },
          ticks: {
            maxTicksLimit: 10,
            callback: function (val, index) {
              return index % Math.ceil(processedData.length / 10) === 0
                ? this.getLabelForValue(val as number)
                : ''
            },
          },
        },
        y: {
          title: { display: true, text: props.yAxisLabel },
          ticks: {
            callback: value => (value as number).toFixed(2) + '%',
            maxTicksLimit: 8,
          },
        },
      },
      plugins: {
        title: { display: false },
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: context => `${props.yAxisLabel}: ${context.parsed.y?.toFixed(2) ?? '0.00'}%`,
          },
        },
      },
    },
  })
}

watch(() => props.data, createChart, { deep: true })

onMounted(() => {
  createChart()
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.destroy()
  }
})
</script>
