import { computed, ref } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'
import { portfolioSummaryService } from '../services'

export function usePortfolioSummary() {
  const summaryService = portfolioSummaryService
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
    } catch (_err) {
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

      const currentDate = currentSummary.date
      const existingIndex = summaries.value.findIndex(item => item.date === currentDate)

      if (existingIndex >= 0) {
        summaries.value[existingIndex] = currentSummary
      } else {
        summaries.value.push(currentSummary)
      }

      summaries.value.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    } catch (_err) {
      error.value = 'Failed to load initial data. Please refresh the page.'
    } finally {
      isLoading.value = false
    }
  }

  const recalculate = async () => {
    isRecalculating.value = true
    recalculationMessage.value = ''

    try {
      const response = await summaryService.recalculateAll()
      recalculationMessage.value = response.message

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

  const reversedSummaries = computed(() => [...summaries.value].reverse())

  return {
    summaries,
    reversedSummaries,
    isLoading,
    isRecalculating,
    isFetching,
    error,
    recalculationMessage,
    hasMoreData,
    recalculate,
    fetchSummaries,
    fetchInitialData,
  }
}
