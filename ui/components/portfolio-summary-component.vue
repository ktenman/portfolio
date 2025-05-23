<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4>Portfolio Summary</h4>
      <button
        class="btn btn-secondary btn-sm"
        @click="recalculateSummaries"
        :disabled="isRecalculating || isLoading"
      >
        <span
          v-if="isRecalculating"
          class="spinner-border spinner-border-sm me-1"
          role="status"
          aria-hidden="true"
        ></span>
        {{ isRecalculating ? 'Recalculating...' : 'Recalculate All Data' }}
      </button>
    </div>

    <!-- Loading indicator -->
    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <!-- Error message - show only if there's an error and not loading -->
    <div v-if="error && !isLoading" class="alert alert-danger" role="alert">
      {{ error }}
    </div>

    <!-- Recalculation message -->
    <div
      v-if="recalculationMessage && !error"
      class="alert alert-info alert-dismissible fade show mt-3"
      role="alert"
    >
      {{ recalculationMessage }}
      <button
        type="button"
        class="btn-close"
        @click="recalculationMessage = ''"
        aria-label="Close"
      ></button>
    </div>

    <!-- Only show content when not loading and no error -->
    <div v-if="!isLoading && !error">
      <!-- No data message - only show when confirmed no data, not loading, and no error -->
      <div v-if="summaryData.length === 0" class="alert alert-info" role="alert">
        No portfolio summary data found.
      </div>

      <!-- Regular content when we have data -->
      <div v-else>
        <div class="mb-3 chart-container" v-if="chartData">
          <Line :data="chartData" :options="chartOptions" />
        </div>

        <div class="table-responsive">
          <table class="table table-striped">
            <thead>
              <tr>
                <th>Date</th>
                <th>XIRR Annual Return</th>
                <th class="hide-on-mobile">Earnings Per Day</th>
                <th>Earnings Per Month</th>
                <th>Total Profit</th>
                <th>Total Value</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(summary, index) in reversedSummaryData"
                :key="summary.date"
                :class="{
                  'font-weight-bold':
                    index === 0 && summary.date === new Date().toISOString().split('T')[0],
                }"
              >
                <td>{{ formatDate(summary.date) }}</td>
                <td>{{ formatPercentage(summary.xirrAnnualReturn) }}</td>
                <td class="hide-on-mobile">{{ formatCurrency(summary.earningsPerDay) }}</td>
                <td>{{ formatCurrency(summary.earningsPerMonth) }}</td>
                <td>{{ formatCurrency(summary.totalProfit) }}</td>
                <td>{{ formatCurrency(summary.totalValue) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="isFetching" class="text-center mt-3">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading more data...</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'
import { Line } from 'vue-chartjs'
import {
  CategoryScale,
  Chart as ChartJS,
  ChartOptions,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Title,
  Tooltip,
} from 'chart.js'
import { SummaryService } from '../services/summary-service.ts'
import { CACHE_KEYS } from '../constants/cache-keys.ts'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Title, Legend)

const summaryData = ref<PortfolioSummary[]>([])
const isLoading = ref(true)
const isFetching = ref(false)
const currentPage = ref(0)
const pageSize = 40
const hasMoreData = ref(true)
const error = ref<string | null>(null)
const summaryService = new SummaryService()

// New refs for recalculation functionality
const isRecalculating = ref(false)
const recalculationMessage = ref('')

async function fetchSummaries() {
  if (isFetching.value || !hasMoreData.value) return
  isFetching.value = true
  error.value = null
  try {
    const response = await summaryService.fetchHistoricalSummary(currentPage.value, pageSize)
    summaryData.value = [...summaryData.value, ...response.content]
    currentPage.value++
    hasMoreData.value = currentPage.value < response.totalPages
    summaryData.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  } catch (err) {
    error.value = 'Failed to fetch summary data. Please try again later.'
  } finally {
    isFetching.value = false
  }
}

async function recalculateSummaries() {
  if (
    !confirm(
      'This will delete all current summary data and recalculate it from scratch. This operation may take some time. Continue?'
    )
  ) {
    return
  }

  isRecalculating.value = true
  recalculationMessage.value = ''

  try {
    // Clear any cached data
    localStorage.removeItem(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
    localStorage.removeItem(CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL)
    localStorage.removeItem(CACHE_KEYS.INSTRUMENTS)

    const response = await summaryService.recalculateAllSummaries()
    recalculationMessage.value = response.message

    // Reset and refresh all data
    currentPage.value = 0
    summaryData.value = []
    hasMoreData.value = true
    await fetchSummaries()

    // Get fresh current summary
    const currentSummary = await summaryService.fetchCurrentSummary()

    // Ensure it's in the display data
    const currentDate = currentSummary.date
    const existingIndex = summaryData.value.findIndex(item => item.date === currentDate)

    if (existingIndex >= 0) {
      summaryData.value[existingIndex] = currentSummary
    } else {
      summaryData.value.push(currentSummary)
    }

    // Sort the data
    summaryData.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  } catch (err) {
    recalculationMessage.value = 'Failed to recalculate summaries. Please try again later.'
    console.error('Error during recalculation:', err)
  } finally {
    isRecalculating.value = false
  }
}

const handleScroll = async () => {
  if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
    await fetchSummaries()
  }
}

onMounted(async () => {
  try {
    await fetchSummaries()
    const currentSummary = await summaryService.fetchCurrentSummary()

    // Always replace or add the current day's data
    const currentDate = currentSummary.date
    const existingIndex = summaryData.value.findIndex(item => item.date === currentDate)

    if (existingIndex >= 0) {
      // Replace existing data for today with current data
      summaryData.value[existingIndex] = currentSummary
    } else {
      // Add today's data if not present
      summaryData.value.push(currentSummary)
    }

    summaryData.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  } catch (err) {
    error.value = 'Failed to load initial data. Please refresh the page.'
  } finally {
    isLoading.value = false
  }
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
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

const modifiedAsap = (data: number[], maxPoints: number): number[] => {
  const step = Math.ceil(data.length / maxPoints)
  return Array.from({ length: maxPoints }, (_, i) => i * step).filter(i => i < data.length)
}

const processedChartData = computed(() => {
  if (summaryData.value.length === 0) return null
  const labels = summaryData.value.map(item => formatDate(item.date))
  const totalValues = summaryData.value.map(item => item.totalValue)
  const profitValues = summaryData.value.map(item => item.totalProfit)
  const xirrValues = summaryData.value.map(item => item.xirrAnnualReturn * 100)
  const earningsValues = summaryData.value.map(item => item.earningsPerMonth)

  const maxPoints = Math.min(window.innerWidth >= 1000 ? 31 : 15, labels.length)
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

<style scoped>
@media (max-width: 767px) {
  .table {
    font-size: 12px;
  }

  .hide-on-mobile {
    display: none;
  }
}

@media (min-width: 1000px) {
  .chart-container {
    height: 400px;
  }
}
</style>
