<template>
  <div class="container mt-3">
    <h2 class="mb-4">Portfolio Summary</h2>

    <div v-if="isLoading" class="text-center my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else>
      <div class="mb-4">
        <h3>Portfolio Performance Chart</h3>
        <LineChart :chart-data="chartData" :options="chartOptions"/>
      </div>

      <div class="table-responsive">
        <table class="table table-striped table-hover">
          <thead>
          <tr>
            <th>Date</th>
            <th>Total Value</th>
            <th>XIRR Annual Return</th>
            <th>Total Profit</th>
            <th>Earnings Per Day</th>
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
import {computed, onMounted, ref} from 'vue'
import {Line as LineChart} from 'vue-chartjs'
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
import {fetchPortfolioSummary} from '../services/portfolio-summary-service.ts'
import {PortfolioSummary} from '../models/portfolio-summary.ts'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const summaryData = ref<PortfolioSummary[]>([])
const isLoading = ref(true)

onMounted(async () => {
  try {
    summaryData.value = await fetchPortfolioSummary()
  } catch (error) {
    console.error('Error fetching portfolio summary:', error)
  } finally {
    isLoading.value = false
  }
})

const chartData = computed(() => ({
  labels: summaryData.value.map(summary => formatDate(summary.date)),
  datasets: [
    {
      label: 'Total Value',
      borderColor: '#36A2EB',
      data: summaryData.value.map(summary => summary.totalValue),
    },
    {
      label: 'Total Profit',
      borderColor: '#FF6384',
      data: summaryData.value.map(summary => summary.totalProfit),
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
}

const formatDate = (date: string) => new Date(date).toLocaleDateString()
const formatCurrency = (value: number) => `$${value.toFixed(2)}`
const formatPercentage = (value: number) => `${(value * 100).toFixed(2)}%`
</script>
