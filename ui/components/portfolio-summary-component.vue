<template>
  <div class="container mt-3">
    <h2 class="mb-4">Portfolio Summary</h2>

    <div v-if="isLoading" class="text-left my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else>
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
import { onMounted, ref } from 'vue'
import { fetchPortfolioSummary } from '../services/portfolio-summary-service.ts'
import { PortfolioSummary } from '../models/portfolio-summary.ts'

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

const formatDate = (date: string) => new Date(date).toLocaleDateString()
const formatCurrency = (value: number) => `$${value.toFixed(2)}`
const formatPercentage = (value: number) => `${(value * 100).toFixed(2)}%`
</script>
