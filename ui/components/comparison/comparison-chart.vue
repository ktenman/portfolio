<template>
  <div class="chart-wrapper" v-if="chartData">
    <Line :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
import { Line } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import '../../plugins/chart'

defineProps<{
  chartData: ChartData<'line'> | null
  chartOptions: ChartOptions<'line'>
}>()
</script>

<style scoped>
.chart-wrapper {
  position: relative;
  height: 400px;
}

@media (min-width: 992px) {
  .chart-wrapper {
    height: 500px;
  }
}
</style>
