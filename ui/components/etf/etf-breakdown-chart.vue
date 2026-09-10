<template>
  <div class="card border-0! shadow-[0_0.125rem_0.25rem_rgb(0_0_0/0.075)]">
    <div class="card-body p-4! sm:p-6!">
      <div class="chart-header mb-4">
        <slot name="actions" />
      </div>
      <breakdown-bars v-if="view === 'bars'" :rows="chartData" :benchmark-label="benchmarkLabel" />
      <breakdown-donut v-else :rows="chartData" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import BreakdownBars from '../shared/breakdown-bars.vue'
import BreakdownDonut from '../shared/breakdown-donut.vue'
import type { BreakdownView } from '../shared/view-switch.vue'
import type { ChartDataItem } from '../../services/etf-chart-service'

defineProps<{
  chartData: ChartDataItem[]
  view?: BreakdownView
  benchmarkLabel?: string
}>()
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

@media (max-width: 768px) {
  .chart-header {
    justify-content: center;
  }
}
</style>
