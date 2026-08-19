import { computed, ref, type Ref } from 'vue'
import {
  useQuery,
  useMutation,
  useQueryClient,
  useInfiniteQuery,
  keepPreviousData,
} from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'
import {
  mergeHistoricalWithCurrent,
  sortSummariesByDateAsc,
  flattenPages,
} from '../services/summary-aggregator'
import { useAuthState } from './use-auth-state'
import { DEFAULT_CHART_RANGE } from './use-time-range'
import { REFETCH_INTERVALS } from '../constants/api'
import type { TimeRange } from '../models/generated/domain-models'

export function usePortfolioSummaryQuery(
  selectedPlatforms?: Ref<string[]>,
  selectedRange?: Ref<TimeRange>
) {
  const queryClient = useQueryClient()
  const recalculationMessage = ref('')
  const pageSize = 30
  const { isAuthenticated } = useAuthState()

  const platformsKey = computed(() => selectedPlatforms?.value ?? [])
  const activePlatforms = computed(() =>
    platformsKey.value.length > 0 ? platformsKey.value : undefined
  )
  const rangeKey = computed(() => selectedRange?.value ?? DEFAULT_CHART_RANGE)

  const {
    data: historicalData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading: isLoadingHistorical,
    error: historicalError,
  } = useInfiniteQuery({
    queryKey: ['portfolio-summary', 'historical', platformsKey],
    queryFn: ({ pageParam = 0 }) =>
      portfolioSummaryService.getHistorical(pageParam, pageSize, activePlatforms.value),
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
    queryKey: ['portfolio-summary', 'current', platformsKey],
    queryFn: () => portfolioSummaryService.getCurrent(activePlatforms.value),
    enabled: isAuthenticated,
    refetchInterval: REFETCH_INTERVALS.SUMMARY,
  })

  const {
    data: seriesData,
    isFetching: isFetchingSeries,
    error: seriesError,
  } = useQuery({
    queryKey: ['portfolio-summary', 'series', platformsKey, rangeKey],
    queryFn: () => portfolioSummaryService.getSeries(rangeKey.value, activePlatforms.value),
    placeholderData: keepPreviousData,
    enabled: isAuthenticated,
  })

  const { data: rangeChange, error: rangeChangeError } = useQuery({
    queryKey: ['portfolio-summary', 'range-change', platformsKey, rangeKey],
    queryFn: () => portfolioSummaryService.getRangeChange(rangeKey.value, activePlatforms.value),
    placeholderData: keepPreviousData,
    enabled: isAuthenticated,
    refetchInterval: REFETCH_INTERVALS.SUMMARY,
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

  const chartSummaries = computed(() =>
    mergeHistoricalWithCurrent(seriesData.value ?? [], currentSummary.value)
  )

  const sortedSummaries = computed(() => sortSummariesByDateAsc(summaries.value))

  const reversedSummaries = computed(() => [...sortedSummaries.value].reverse())

  const isLoading = computed(() => isLoadingHistorical.value || isLoadingCurrent.value)
  const error = computed(() => historicalError.value?.message || null)
  const rangeError = computed(
    () => seriesError.value?.message || rangeChangeError.value?.message || null
  )

  return {
    summaries,
    chartSummaries,
    rangeChange,
    sortedSummaries,
    reversedSummaries,
    isLoading,
    isFetching: isFetchingNextPage,
    isRangeLoading: isFetchingSeries,
    isRecalculating: recalculateMutation.isPending,
    error,
    rangeError,
    recalculationMessage,
    hasMoreData: hasNextPage,
    fetchSummaries: fetchNextPage,
    recalculate: () => recalculateMutation.mutate(),
  }
}
