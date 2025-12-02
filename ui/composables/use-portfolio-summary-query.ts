import { computed, ref } from 'vue'
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'

export function usePortfolioSummaryQuery() {
  const queryClient = useQueryClient()
  const recalculationMessage = ref('')
  const pageSize = 186

  const {
    data: historicalData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading: isLoadingHistorical,
    error: historicalError,
  } = useInfiniteQuery({
    queryKey: ['portfolio-summary', 'historical'],
    queryFn: ({ pageParam = 0 }) => portfolioSummaryService.getHistorical(pageParam, pageSize),
    getNextPageParam: (lastPage, allPages) => {
      if (allPages.length < lastPage.totalPages) {
        return allPages.length
      }
      return undefined
    },
    initialPageParam: 0,
  })

  const { data: currentSummary, isLoading: isLoadingCurrent } = useQuery({
    queryKey: ['portfolio-summary', 'current'],
    queryFn: portfolioSummaryService.getCurrent,
  })

  const recalculateMutation = useMutation({
    mutationFn: portfolioSummaryService.recalculate,
    onSuccess: response => {
      recalculationMessage.value = response.message
      queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
      queryClient.invalidateQueries({ queryKey: ['instruments'] })
    },
    onError: () => {
      recalculationMessage.value = 'Failed to recalculate summaries. Please try again later.'
    },
  })

  const summaries = computed(() => {
    const historicalSummaries = historicalData.value?.pages.flatMap(page => page.content) || []

    if (currentSummary.value) {
      const existingIndex = historicalSummaries.findIndex(
        item => item.date === currentSummary.value.date
      )
      if (existingIndex >= 0) {
        historicalSummaries[existingIndex] = currentSummary.value
      } else {
        historicalSummaries.push(currentSummary.value)
      }
    }

    return historicalSummaries
  })

  const sortedSummaries = computed(() =>
    [...summaries.value].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  )

  const reversedSummaries = computed(() => [...sortedSummaries.value].reverse())

  const isLoading = computed(() => isLoadingHistorical.value || isLoadingCurrent.value)
  const error = computed(() => historicalError.value?.message || null)

  return {
    summaries,
    sortedSummaries,
    reversedSummaries,
    isLoading,
    isFetching: isFetchingNextPage,
    isRecalculating: recalculateMutation.isPending,
    error,
    recalculationMessage,
    hasMoreData: hasNextPage,
    fetchSummaries: fetchNextPage,
    recalculate: () => recalculateMutation.mutate(),
  }
}
