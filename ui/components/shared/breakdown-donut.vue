<template>
  <div class="chart-body">
    <div class="chart-container">
      <canvas ref="chartCanvas"></canvas>
      <div v-if="activeItem" class="chart-centre" aria-hidden="true">
        <span class="chart-centre-label">{{ activeItem.label }}</span>
        <span class="chart-centre-value">{{ formatPercentage(activeItem.value) }}</span>
      </div>
    </div>
    <breakdown-legend
      :items="rows"
      :active-index="activeIndex"
      @hover="focusSlice"
      @leave="clearSlice"
    />
  </div>
</template>

<script lang="ts">
import { Chart, DoughnutController, ArcElement } from 'chart.js'

Chart.register(DoughnutController, ArcElement)
</script>

<script lang="ts" setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import BreakdownLegend from './breakdown-legend.vue'
import { withAlpha } from '../../constants/chart-colors'
import { formatPercentage } from '../../utils/formatters'
import type { BreakdownRow } from '../../services/diversification-chart-service'

const props = defineProps<{
  rows: BreakdownRow[]
}>()

const chartCanvas = ref<HTMLCanvasElement | null>(null)
const activeIndex = ref<number | null>(null)
let chart: Chart | null = null

const activeItem = computed(() =>
  activeIndex.value === null ? null : (props.rows[activeIndex.value] ?? null)
)

const RING_FRACTION = 1 / 6
const DIMMED_OPACITY = 0.3
const GAP_PX = 6

const fills = (active: number | null) =>
  props.rows.map((row, index) =>
    active === null || active === index ? row.color : withAlpha(row.color, DIMMED_OPACITY)
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
  if (!chartCanvas.value || props.rows.length === 0) return

  if (chart) {
    chart.destroy()
  }

  const painted = fills(activeIndex.value)

  chart = new Chart(chartCanvas.value, {
    type: 'doughnut',
    data: {
      labels: props.rows.map(row => row.label),
      datasets: [
        {
          data: props.rows.map(row => row.value),
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
  if (!chart?.data?.datasets?.[0] || props.rows.length === 0) {
    renderChart()
    return
  }
  const painted = fills(null)
  chart.data.labels = props.rows.map(row => row.label)
  chart.data.datasets[0].data = props.rows.map(row => row.value)
  chart.data.datasets[0].backgroundColor = painted
  chart.data.datasets[0].hoverBackgroundColor = painted
  chart.setActiveElements([])
  chart.update('none')
}

onMounted(() => {
  renderChart()
})

watch(
  () => props.rows,
  () => {
    updateChartData()
  }
)

onBeforeUnmount(() => {
  chart?.destroy()
  chart = null
})
</script>

<style scoped>
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
