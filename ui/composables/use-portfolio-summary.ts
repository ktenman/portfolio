import { computed, reactive, toRefs } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'
import { portfolioSummaryService } from '../services'

type LoadingState = 'idle' | 'loading' | 'paginating' | 'recalculating' | 'error'

interface PortfolioState {
  summaries: PortfolioSummary[]
  status: LoadingState
  error: string | null
  currentPage: number
  hasMoreData: boolean
  recalculationMessage: string
}

export function usePortfolioSummary() {
  const summaryService = portfolioSummaryService
  const pageSize = 40

  const state = reactive<PortfolioState>({
    summaries: [],
    status: 'loading',
    error: null,
    currentPage: 0,
    hasMoreData: true,
    recalculationMessage: '',
  })

  const sortedSummaries = computed(() =>
    [...state.summaries].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  )

  const reversedSummaries = computed(() => [...sortedSummaries.value].reverse())

  const isLoading = computed(() => state.status === 'loading')
  const isFetching = computed(() => state.status === 'paginating')
  const isRecalculating = computed(() => state.status === 'recalculating')

  const fetchSummaries = async () => {
    if (state.status === 'paginating' || !state.hasMoreData) return

    state.status = state.currentPage === 0 ? 'loading' : 'paginating'
    state.error = null

    try {
      const response = await summaryService.getHistorical(state.currentPage, pageSize)
      state.summaries.push(...response.content)
      state.currentPage++
      state.hasMoreData = state.currentPage < response.totalPages
      state.status = 'idle'
    } catch (_err) {
      state.error = 'Failed to fetch summary data. Please try again later.'
      state.status = 'error'
    }
  }

  const fetchInitialData = async () => {
    state.status = 'loading'
    state.error = null

    try {
      const [historicalResponse, currentSummary] = await Promise.all([
        summaryService.getHistorical(0, pageSize),
        summaryService.getCurrent(),
      ])

      state.summaries = historicalResponse.content
      state.currentPage = 1
      state.hasMoreData = state.currentPage < historicalResponse.totalPages

      const existingIndex = state.summaries.findIndex(item => item.date === currentSummary.date)
      if (existingIndex >= 0) {
        state.summaries[existingIndex] = currentSummary
      } else {
        state.summaries.push(currentSummary)
      }

      state.status = 'idle'
    } catch (_err) {
      state.error = 'Failed to load initial data. Please refresh the page.'
      state.status = 'error'
    }
  }

  const recalculate = async () => {
    state.status = 'recalculating'
    state.recalculationMessage = ''

    try {
      const response = await summaryService.recalculateAll()
      state.recalculationMessage = response.message

      state.currentPage = 0
      state.summaries = []
      state.hasMoreData = true

      await fetchInitialData()
      return response.message
    } catch (err) {
      state.recalculationMessage = 'Failed to recalculate summaries. Please try again later.'
      state.status = 'error'
      console.error('Error during recalculation:', err)
    }
  }

  return {
    ...toRefs(state),
    sortedSummaries,
    reversedSummaries,
    isLoading,
    isRecalculating,
    isFetching,
    recalculate,
    fetchSummaries,
    fetchInitialData,
  }
}
