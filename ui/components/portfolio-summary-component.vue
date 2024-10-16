<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4>Portfolio Summary</h4>
    </div>

    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <div v-else>
      <div v-if="summaryData.length === 0" class="alert alert-info" role="alert">
        No portfolio summary data found.
      </div>
      <div v-else>
        <div class="mb-3 chart-container">
          <Line v-if="chartData" :data="chartData" :options="chartOptions as any" />
        </div>

        <div class="table-responsive">
          <table class="table table-striped table-hover">
            <thead>
              <tr>
                <th>Date</th>
                <th>Total</th>
                <th>Profit</th>
                <th>XIRR</th>
                <th>Per Month</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="summary in reversedSummaryData" :key="summary.date">
                <td>{{ formatDate(summary.date) }}</td>
                <td>{{ formatCurrency(summary.totalValue) }}</td>
                <td>{{ formatCurrency(summary.totalProfit) }}</td>
                <td>{{ formatPercentage(summary.xirrAnnualReturn) }}</td>
                <td>{{ formatCurrency(summary.earningsPerMonth) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, shallowRef } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'
import { Line } from 'vue-chartjs'
import {
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Title,
  Tooltip,
} from 'chart.js'
import { SummaryService } from '../services/summary-service.ts'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Title, Legend)

const summaryData = shallowRef<PortfolioSummary[]>([])
const isLoading = ref(true)
const summaryService = new SummaryService()

// Modified ASAP algorithm implementation
function modifiedAsap(timeSeries: number[], maxPoints: number): number[] {
  const n = timeSeries.length
  if (n <= maxPoints) return Array.from(Array(n).keys()) // Return all indices if series is already short enough

  const step = Math.ceil(n / maxPoints)
  const baseIndices = Array.from(Array(Math.floor(n / step)).keys()).map(i => i * step)

  // Always include the first and last points
  return Array.from(new Set([0, ...baseIndices, n - 1])).sort((a, b) => a - b)
}

onMounted(async () => {
  try {
    summaryData.value = await summaryService.fetchAllSummaries()
    summaryData.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  } catch (error) {
    console.error('Error fetching portfolio summary:', error)
  } finally {
    isLoading.value = false
  }
})

const reversedSummaryData = computed(() => {
  return [...summaryData.value].reverse()
})

const formatDate = (date: string): string => {
  const dateObj = new Date(date)
  const day = String(dateObj.getDate()).padStart(2, '0')
  const month = String(dateObj.getMonth() + 1).padStart(2, '0')
  const year = String(dateObj.getFullYear()).slice(-2)
  return `${day}.${month}.${year}`
}

const formatCurrency = (value: number) => `€${value.toFixed(2)}`
const formatPercentage = (value: number) => `${(value * 100).toFixed(2)}%`

const processedChartData = computed(() => {
  if (summaryData.value.length === 0) return null
  const labels = summaryData.value.map(item => formatDate(item.date))
  const totalValues = summaryData.value.map(item => item.totalValue)
  const profitValues = summaryData.value.map(item => item.totalProfit)
  const xirrValues = summaryData.value.map(item => item.xirrAnnualReturn * 100)
  const earningsValues = summaryData.value.map(item => item.earningsPerMonth)

  // Apply modified ASAP algorithm to each series
  const maxPoints = Math.min(31, labels.length) // Adjust this value to control the maximum number of points
  const indices = modifiedAsap(totalValues, maxPoints)

  return {
    labels: indices.map(i => labels[i]),
    totalValues: indices.map(i => totalValues[i]),
    profitValues: indices.map(i => profitValues[i]),
    xirrValues: indices.map(i => xirrValues[i]),
    earningsValues: indices.map(i => earningsValues[i]),
  }
})

const chartData = computed(() => {
  const data = processedChartData.value
  if (!data) return null

  return {
    labels: data.labels,
    datasets: [
      {
        label: 'Total Value',
        borderColor: '#8884d8',
        data: data.totalValues,
        yAxisID: 'y',
      },
      {
        label: 'Total Profit',
        borderColor: '#ffc658',
        data: data.profitValues,
        yAxisID: 'y',
      },
      {
        label: 'XIRR Annual Return',
        borderColor: '#82ca9d',
        data: data.xirrValues,
        yAxisID: 'y1',
      },
      {
        label: 'Earnings Per Month',
        borderColor: '#ff7300',
        data: data.earningsValues,
        yAxisID: 'y',
      },
    ],
  }
})
const chartOptions = {
  responsive: true,
  animation: false,
  interaction: {
    mode: 'index' as const,
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
        text: 'Amount ($)',
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

<style scoped>
@media (max-width: 767px) {
  .table {
    font-size: 12px;
  }
}

@media (min-width: 1000px) {
  .chart-container {
    height: 50vh;
    margin: 0 auto;
  }
}
</style>
