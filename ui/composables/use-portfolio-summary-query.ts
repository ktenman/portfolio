import { computed, ref, type Ref } from 'vue'
import {
  useQuery,
  useMutation,
  useQueryClient,
  useInfiniteQuery,
  keepPreviousData,
} from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/api'
import {
  mergeHistoricalWithCurrent,
  sortSummariesByDateAsc,
  flattenPages,
} from '../services/summary-aggregator'
import { useAuthState } from './use-auth-state'
import { DEFAULT_CHART_RANGE } from './use-time-range'
import { BENCHMARKS, type ChartBenchmark, type ChartSummary } from './use-portfolio-chart'
import { REFETCH_INTERVALS } from '../constants/api'
import { type BenchmarkIndex, TimeRange } from '../models/generated/domain-models'

const INTRADAY_RANGES: TimeRange[] = [
  TimeRange.ONE_DAY,
  TimeRange.TWO_DAYS,
  TimeRange.THREE_DAYS,
  TimeRange.FOUR_DAYS,
  TimeRange.FIVE_DAYS,
  TimeRange.SIX_DAYS,
]

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

  const isIntradayRange = computed(() => INTRADAY_RANGES.includes(rangeKey.value))

  const { data: intradayData } = useQuery({
    queryKey: ['portfolio-summary', 'intraday', platformsKey, rangeKey],
    queryFn: () => portfolioSummaryService.getIntraday(rangeKey.value, activePlatforms.value),
    placeholderData: keepPreviousData,
    enabled: computed(() => isAuthenticated.value && isIntradayRange.value),
    refetchInterval: REFETCH_INTERVALS.SUMMARY,
  })

  const benchmarkQuery = (index: BenchmarkIndex) =>
    useQuery({
      queryKey: ['portfolio-summary', 'benchmark', rangeKey, index],
      queryFn: () => portfolioSummaryService.getBenchmark(rangeKey.value, index),
      placeholderData: keepPreviousData,
      enabled: isAuthenticated,
    })

  const benchmarkQueries = BENCHMARKS.map(benchmark => ({
    benchmark,
    query: benchmarkQuery(benchmark.index),
  }))

  const { data: rangeChange, error: rangeChangeError } = useQuery({
    queryKey: ['portfolio-summary', 'range-change', platformsKey, rangeKey],
    queryFn: async () => ({
      range: rangeKey.value,
      platforms: platformsKey.value.join(','),
      ...(await portfolioSummaryService.getRangeChange(rangeKey.value, activePlatforms.value)),
    }),
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

  const performanceSummaries = computed(() =>
    mergeHistoricalWithCurrent(seriesData.value ?? [], currentSummary.value)
  )

  const intradaySummaries = computed<ChartSummary[]>(() => {
    if (!isIntradayRange.value) return []
    return (intradayData.value ?? []).map(point => ({
      date: point.capturedAt,
      totalValue: point.totalValue,
      totalProfit: point.totalProfit,
      xirrAnnualReturn: point.xirrAnnualReturn,
      earningsPerMonth: point.earningsPerMonth,
    }))
  })

  const chartSummaries = computed<ChartSummary[]>(() =>
    intradaySummaries.value.length > 0 ? intradaySummaries.value : performanceSummaries.value
  )

  const benchmarks = computed<ChartBenchmark[]>(() =>
    benchmarkQueries.map(({ benchmark, query }) => ({
      key: benchmark.key,
      label: benchmark.label,
      color: benchmark.color,
      points: query.data.value ?? [],
    }))
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
    performanceSummaries,
    benchmarks,
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
