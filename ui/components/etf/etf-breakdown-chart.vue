<template>
  <div class="card border-0! shadow-[0_0.125rem_0.25rem_rgb(0_0_0/0.075)]">
    <div class="card-body p-6!">
      <div class="chart-header mb-4">
        <slot name="actions" />
      </div>
      <div class="chart-body">
        <div class="chart-container">
          <canvas ref="chartCanvas"></canvas>
          <div v-if="activeItem" class="chart-centre" aria-hidden="true">
            <span class="chart-centre-label">{{ activeItem.label }}</span>
            <span class="chart-centre-value">{{ activeItem.percentage }}%</span>
          </div>
        </div>
        <etf-breakdown-legend
          :items="chartData"
          :active-index="activeIndex"
          :benchmark-label="benchmarkLabel"
          @hover="focusSlice"
          @leave="clearSlice"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Chart, DoughnutController, ArcElement } from 'chart.js'

Chart.register(DoughnutController, ArcElement)
</script>

<script lang="ts" setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import EtfBreakdownLegend from './etf-breakdown-legend.vue'
import { withAlpha } from '../../constants/chart-colors'
import type { ChartDataItem } from '../../services/etf-chart-service'

const props = defineProps<{
  chartData: ChartDataItem[]
  benchmarkLabel?: string
}>()

const chartCanvas = ref<HTMLCanvasElement | null>(null)
const activeIndex = ref<number | null>(null)
let chart: Chart | null = null

const activeItem = computed(() =>
  activeIndex.value === null ? null : (props.chartData[activeIndex.value] ?? null)
)

const RING_FRACTION = 1 / 6
const DIMMED_OPACITY = 0.3
const GAP_PX = 6

const fills = (active: number | null) =>
  props.chartData.map((item, index) =>
    active === null || active === index ? item.color : withAlpha(item.color, DIMMED_OPACITY)
  )

const shapeArcs = {
  id: 'shapeArcs',
  afterUpdate: (instance: Chart<'doughnut'>) => {
    const arcs = instance.getDatasetMeta(0).data as ArcElement[]
    arcs.forEach((arc, index) => {
      const rings = index === activeIndex.value ? 2 : 1
      arc.innerRadius = arc.outerRadius * (1 - rings * RING_FRACTION)
      const gap = Math.min(GAP_PX / arc.outerRadius, (arc.endAngle - arc.startAngle) * 0.6)
      arc.startAngle += gap / 2
      arc.endAngle -= gap / 2
      arc.circumference = arc.endAngle - arc.startAngle
    })
  },
}

const focusSlice = (index: number | null) => {
  if (activeIndex.value === index) return
  activeIndex.value = index
  const dataset = chart?.data?.datasets?.[0]
  if (!chart || !dataset) return
  const painted = fills(index)
  dataset.backgroundColor = painted
  dataset.hoverBackgroundColor = painted
  chart.setActiveElements(index === null ? [] : [{ datasetIndex: 0, index }])
  chart.update('none')
}

const clearSlice = () => focusSlice(null)

const renderChart = () => {
  if (!chartCanvas.value || props.chartData.length === 0) return

  if (chart) {
    chart.destroy()
  }

  const painted = fills(activeIndex.value)

  chart = new Chart(chartCanvas.value, {
    type: 'doughnut',
    data: {
      labels: props.chartData.map(item => item.label),
      datasets: [
        {
          data: props.chartData.map(item => item.value),
          backgroundColor: painted,
          borderWidth: 0,
          hoverBackgroundColor: painted,
          borderRadius: 0,
        },
      ],
    },
    plugins: [shapeArcs],
    options: {
      responsive: true,
      maintainAspectRatio: true,
      animation: false,
      cutout: '83.333%',
      plugins: {
        legend: { display: false },
        tooltip: { enabled: false },
      },
      onHover: (_event, elements) => {
        focusSlice(elements.length > 0 ? elements[0].index : null)
      },
    },
  })
}

const updateChartData = () => {
  activeIndex.value = null
  if (!chart?.data?.datasets?.[0] || props.chartData.length === 0) {
    renderChart()
    return
  }
  const painted = fills(null)
  chart.data.labels = props.chartData.map(item => item.label)
  chart.data.datasets[0].data = props.chartData.map(item => item.value)
  chart.data.datasets[0].backgroundColor = painted
  chart.data.datasets[0].hoverBackgroundColor = painted
  chart.setActiveElements([])
  chart.update('none')
}

onMounted(() => {
  renderChart()
})

watch(
  () => props.chartData,
  () => {
    updateChartData()
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
  border: 1px solid var(--color-hairline);
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
  flex-wrap: wrap;
}

.chart-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1.5rem;
}

.chart-container {
  position: relative;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-centre {
  position: absolute;
  top: 22%;
  bottom: 22%;
  left: 50%;
  transform: translateX(-50%);
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.125rem;
  pointer-events: none;
  text-align: center;
}

.chart-centre-label {
  max-width: 100%;
  font-size: 0.8125rem;
  line-height: 1.2;
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-centre-value {
  font-size: var(--text-title);
  font-weight: 550;
  line-height: 1.1;
  color: var(--color-ink);
}

@media (min-width: 768px) {
  .chart-body {
    grid-template-columns: minmax(0, 20rem) minmax(0, 1fr);
    align-items: center;
  }
}

@media (max-width: 768px) {
  .chart-container {
    height: 250px;
  }
}
</style>
