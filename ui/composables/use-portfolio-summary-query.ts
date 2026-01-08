import { computed, ref } from 'vue'
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'
import {
  mergeHistoricalWithCurrent,
  sortSummariesByDateAsc,
  flattenPages,
} from '../services/summary-aggregator'
import { useAuthState } from './use-auth-state'

export function usePortfolioSummaryQuery() {
  const queryClient = useQueryClient()
  const recalculationMessage = ref('')
  const pageSize = 186
  const { isAuthenticated } = useAuthState()

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
    enabled: isAuthenticated,
  })

  const { data: currentSummary, isLoading: isLoadingCurrent } = useQuery({
    queryKey: ['portfolio-summary', 'current'],
    queryFn: portfolioSummaryService.getCurrent,
    enabled: isAuthenticated,
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
    const historicalSummaries = flattenPages(historicalData.value?.pages)
    return mergeHistoricalWithCurrent(historicalSummaries, currentSummary.value)
  })

  const sortedSummaries = computed(() => sortSummariesByDateAsc(summaries.value))

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
