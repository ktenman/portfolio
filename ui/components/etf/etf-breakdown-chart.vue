<template>
  <div class="card shadow-sm border-0">
    <div class="card-body p-4">
      <h5 class="chart-title mb-3">{{ title }}</h5>
      <div class="chart-container">
        <canvas ref="chartCanvas"></canvas>
      </div>
      <div class="chart-legend mt-3">
        <div v-for="item in chartData" :key="item.label" class="legend-item">
          <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
          <span class="legend-label">{{ item.label }}</span>
          <span class="legend-value">{{ item.percentage }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { Chart, ArcElement, Tooltip, Legend } from 'chart.js'

Chart.register(ArcElement, Tooltip, Legend)

export interface ChartDataItem {
  label: string
  value: number
  percentage: string
  color: string
}

const props = withDefaults(
  defineProps<{
    title: string
    chartData: ChartDataItem[]
    enhanceSmallSlices?: boolean
  }>(),
  {
    enhanceSmallSlices: false,
  }
)

const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chart: Chart | null = null

const getVisualValues = () => {
  if (!props.enhanceSmallSlices) {
    return props.chartData.map(item => item.value)
  }
  return props.chartData.map(item => Math.sqrt(item.value))
}

const renderChart = () => {
  if (!chartCanvas.value || props.chartData.length === 0) return

  if (chart) {
    chart.destroy()
  }

  const actualValues = props.chartData.map(item => item.value)

  chart = new Chart(chartCanvas.value, {
    type: 'pie',
    data: {
      labels: props.chartData.map(item => item.label),
      datasets: [
        {
          data: getVisualValues(),
          backgroundColor: props.chartData.map(item => item.color),
          borderWidth: 2,
          borderColor: '#ffffff',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      animation: false,
      plugins: {
        legend: {
          display: false,
        },
        tooltip: {
          callbacks: {
            label: context => {
              const label = context.label || ''
              const value = actualValues[context.dataIndex] || 0
              return `${label}: ${value.toFixed(2)}%`
            },
          },
        },
      },
    },
  })
}

onMounted(() => {
  renderChart()
})

watch(
  () => props.chartData,
  () => {
    renderChart()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (chart) {
    chart.destroy()
  }
})
</script>

<style scoped>
.card {
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.chart-container {
  position: relative;
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 500px;
  overflow-y: auto;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
}

.legend-label {
  flex: 1;
  color: #495057;
}

.legend-value {
  font-weight: 600;
  color: #1a1a1a;
  margin-left: auto;
}

@media (max-width: 768px) {
  .chart-container {
    height: 250px;
  }
}
</style>
