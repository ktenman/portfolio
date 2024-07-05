<template>
  <div class="container mt-3">
    <div v-if="isLoading" class="text-left my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else>
      <div class="mb-5">
        <Line v-if="chartData" :data="chartData" :options="chartOptions" />
      </div>

      <div class="table-responsive">
        <table class="table table-striped table-hover">
          <thead>
            <tr>
              <th>Date</th>
              <th>Total</th>
              <th>XIRR</th>
              <th>Profit</th>
              <th>Per Day</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="summary in summaryData" :key="summary.date">
              <td>{{ formatDate(summary.date) }}</td>
              <td>{{ formatCurrency(summary.totalValue) }}</td>
              <td>{{ formatPercentage(summary.xirrAnnualReturn) }}</td>
              <td>{{ formatCurrency(summary.totalProfit) }}</td>
              <td>{{ formatCurrency(summary.earningsPerDay) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { fetchPortfolioSummary } from '../services/portfolio-summary-service'
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

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const summaryData = ref<PortfolioSummary[]>([])
const isLoading = ref(true)

onMounted(async () => {
  try {
    summaryData.value = await fetchPortfolioSummary()
    summaryData.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  } catch (error) {
    console.error('Error fetching portfolio summary:', error)
  } finally {
    isLoading.value = false
  }
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

const chartData = computed(() => {
  if (summaryData.value.length === 0) return null
  return {
    labels: summaryData.value.map(item => formatDate(item.date)),
    datasets: [
      {
        label: 'Total Value',
        borderColor: '#8884d8',
        data: summaryData.value.map(item => item.totalValue),
        yAxisID: 'y',
      },
      {
        label: 'XIRR Annual Return',
        borderColor: '#82ca9d',
        data: summaryData.value.map(item => item.xirrAnnualReturn * 100),
        yAxisID: 'y1',
      },
      {
        label: 'Total Profit',
        borderColor: '#ffc658',
        data: summaryData.value.map(item => item.totalProfit),
        yAxisID: 'y',
      },
      {
        label: 'Earnings Per Day',
        borderColor: '#ff7300',
        data: summaryData.value.map(item => item.earningsPerDay),
        yAxisID: 'y',
      },
    ],
  }
})

const chartOptions = {
  responsive: true,
  interaction: {
    mode: 'index' as const,
    intersect: false,
  },
  scales: {
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      title: {
        display: true,
        text: 'Amount ($)',
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
</style>
