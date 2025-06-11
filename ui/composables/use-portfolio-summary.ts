import { ref, computed, onMounted, onUnmounted } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'
import { PortfolioSummaryService } from '../services/portfolio-summary-service'
import { CACHE_KEYS } from '../constants/cache-keys'
import { cacheService } from '../services/cache-service'

interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

export function usePortfolioSummary() {
  const summaryService = new PortfolioSummaryService()
  const summaries = ref<PortfolioSummary[]>([])
  const isLoading = ref(true)
  const isRecalculating = ref(false)
  const currentPage = ref(0)
  const pageSize = 40
  const hasMoreData = ref(true)
  const isFetching = ref(false)
  const error = ref<string | null>(null)
  const recalculationMessage = ref('')

  const fetchSummaries = async () => {
    if (isFetching.value || !hasMoreData.value) return

    isFetching.value = true
    error.value = null

    try {
      const response = await summaryService.getHistorical(currentPage.value, pageSize)
      summaries.value = [...summaries.value, ...response.content]
      currentPage.value++
      hasMoreData.value = currentPage.value < response.totalPages
      summaries.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    } catch (err) {
      error.value = 'Failed to fetch summary data. Please try again later.'
    } finally {
      isFetching.value = false
    }
  }

  const fetchInitialData = async () => {
    isLoading.value = true
    try {
      await fetchSummaries()
      const currentSummary = await summaryService.getCurrent()

      // Always replace or add the current day's data
      const currentDate = currentSummary.date
      const existingIndex = summaries.value.findIndex(item => item.date === currentDate)

      if (existingIndex >= 0) {
        summaries.value[existingIndex] = currentSummary
      } else {
        summaries.value.push(currentSummary)
      }

      summaries.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    } catch (err) {
      error.value = 'Failed to load initial data. Please refresh the page.'
    } finally {
      isLoading.value = false
    }
  }

  const recalculate = async () => {
    isRecalculating.value = true
    recalculationMessage.value = ''

    try {
      // Clear relevant caches - this should be handled by the service
      cacheService.clearItem(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
      cacheService.clearItem(CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL)
      cacheService.clearItem(CACHE_KEYS.INSTRUMENTS)

      const response = await summaryService.recalculateAll()
      recalculationMessage.value = response.message

      // Reset and refresh all data
      currentPage.value = 0
      summaries.value = []
      hasMoreData.value = true
      await fetchInitialData()

      return response.message
    } catch (err) {
      recalculationMessage.value = 'Failed to recalculate summaries. Please try again later.'
      console.error('Error during recalculation:', err)
    } finally {
      isRecalculating.value = false
    }
  }

  // Infinite scroll logic
  const handleScroll = async () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
      await fetchSummaries()
    }
  }

  onMounted(() => {
    fetchInitialData()
    window.addEventListener('scroll', handleScroll)
  })

  onUnmounted(() => {
    window.removeEventListener('scroll', handleScroll)
  })

  // Computed properties
  const reversedSummaries = computed(() => [...summaries.value].reverse())

  const modifiedAsap = (data: number[], maxPoints: number): number[] => {
    const step = Math.ceil(data.length / maxPoints)
    return Array.from({ length: maxPoints }, (_, i) => i * step).filter(i => i < data.length)
  }

  const processedChartData = computed<ChartDataPoint | null>(() => {
    if (summaries.value.length === 0) return null

    const labels = summaries.value.map(item => item.date)
    const totalValues = summaries.value.map(item => item.totalValue)
    const profitValues = summaries.value.map(item => item.totalProfit)
    const xirrValues = summaries.value.map(item => item.xirrAnnualReturn * 100)
    const earningsValues = summaries.value.map(item => item.earningsPerMonth)

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

  return {
    summaries,
    reversedSummaries,
    isLoading,
    isRecalculating,
    isFetching,
    error,
    recalculationMessage,
    processedChartData,
    recalculate,
    fetchSummaries,
  }
}
