<template>
  <div class="mb-3 chart-container" v-if="chartData">
    <Line :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import type { ChartOptions } from 'chart.js'
import { formatDate } from '../../utils/formatters'
import '../../plugins/chart'

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

const chartData = computed(() => {
  if (!props.data) return null

  return {
    labels: props.data.labels.map(label => formatDate(label)),
    datasets: [
      {
        label: 'Total Value',
        borderColor: '#8884d8',
        data: props.data.totalValues,
        yAxisID: 'y',
      },
      {
        label: 'Total Profit',
        borderColor: '#ffc658',
        data: props.data.profitValues,
        yAxisID: 'y',
      },
      {
        label: 'XIRR Annual Return',
        borderColor: '#82ca9d',
        data: props.data.xirrValues,
        yAxisID: 'y1',
      },
      {
        label: 'Earnings Per Month',
        borderColor: '#ff7300',
        data: props.data.earningsValues,
        yAxisID: 'y',
      },
    ],
  }
})

const chartOptions: ChartOptions<'line'> = {
  responsive: true,
  animation: false,
  interaction: {
    mode: 'index',
    intersect: false,
  },
  scales: {
    x: {
      ticks: {
        maxTicksLimit: 5,
      },
    },
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      title: {
        display: true,
        text: 'Amount (€)',
      },
      ticks: {
        maxTicksLimit: 8,
      },
    },
    y1: {
      type: 'linear' as const,
      display: true,
      position: 'right' as const,
      title: {
        display: true,
        text: 'XIRR (%)',
      },
      grid: {
        drawOnChartArea: false,
      },
      ticks: {
        maxTicksLimit: 8,
      },
    },
  },
}
</script>

<style lang="scss" scoped>
@use '../../styles/config' as *;

.chart-container {
  @media (min-width: $breakpoint-lg) {
    //height: 45rem;
  }
}
</style>
