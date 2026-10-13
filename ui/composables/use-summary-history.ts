import { computed, ref, toRaw, watch, watchEffect, type Ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/api'
import { mergeSummaryHistory } from '../services/summary-aggregator'
import { CALENDAR_RANGE, lacksDayBeforeLatest } from '../services/daily-profit-calendar'
import { REFETCH_INTERVALS } from '../constants'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'
import type { CalendarHistory } from './use-daily-profit-calendar'

interface CurrentSummaryQuery {
  data: Ref<PortfolioSummaryDto | undefined>
  error: Ref<Error | null>
  refetch: () => Promise<unknown>
}

export function useSummaryHistory(
  platforms: Ref<string[]>,
  isAuthenticated: Ref<boolean>,
  current: CurrentSummaryQuery,
  interval?: Ref<'daily' | 'monthly'>
) {
  const queryClient = useQueryClient()
  const isHistoryRequested = ref(false)
  const calendarRefetchInterval = ref<number | false>(false)
  const historyKey = computed(() => [
    'portfolio-summary',
    'daily-calendar-history',
    platforms.value,
  ])
  const isMonthly = computed(() => interval?.value === 'monthly')
  const platformKey = computed(() => JSON.stringify(platforms.value))
  const observedPlatform = ref(platformKey.value)
  const isCurrentPlatform = computed(() => observedPlatform.value === platformKey.value)

  watch(
    platformKey,
    () => {
      isHistoryRequested.value = false
    },
    { flush: 'sync' }
  )
  watch(
    platformKey,
    key => {
      observedPlatform.value = key
    },
    { flush: 'post' }
  )
  watch(
    [platformKey, isMonthly],
    () => {
      if (!isMonthly.value) return
      const cachedHistory = queryClient.getQueryCache().find({
        queryKey: historyKey.value,
        exact: true,
      })
      if (!cachedHistory?.state.error || cachedHistory.state.data === undefined) return
      cachedHistory.setState({ isInvalidated: false })
    },
    { immediate: true, flush: 'sync' }
  )

  const calendar = useQuery({
    queryKey: ['portfolio-summary', 'daily-calendar', platforms] as const,
    queryFn: ({ queryKey }) =>
      portfolioSummaryService.getSeries(
        CALENDAR_RANGE,
        queryKey[2].length ? queryKey[2] : undefined
      ),
    refetchInterval: calendarRefetchInterval,
    enabled: isAuthenticated,
  })

  const history = useQuery({
    queryKey: ['portfolio-summary', 'daily-calendar-history', platforms] as const,
    queryFn: ({ queryKey }) =>
      portfolioSummaryService.getSeries(
        TimeRange.MAX,
        queryKey[2].length ? queryKey[2] : undefined
      ),
    enabled: computed(() => isAuthenticated.value && (isHistoryRequested.value || isMonthly.value)),
    staleTime: Infinity,
    retry: 1,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  watch(history.error, error => {
    if (error && queryClient.getQueryState(historyKey.value)?.error === toRaw(error)) {
      isHistoryRequested.value = false
    }
  })

  const calendarError = ref<string | null>(null)
  watch(
    platformKey,
    () => {
      calendarError.value = null
    },
    { flush: 'sync' }
  )
  watchEffect(() => {
    if (calendar.error.value) calendarError.value = calendar.error.value.message
    else if (calendar.isSuccess.value) calendarError.value = null
  })

  const mergedRecentRows = computed(() =>
    mergeSummaryHistory([], calendar.data.value ?? [], current.data.value)
  )
  const mergedHistoryRows = computed(() =>
    mergeSummaryHistory(history.data.value ?? [], calendar.data.value ?? [], current.data.value)
  )
  const dailyCalendarRows = computed(() =>
    isCurrentPlatform.value && calendar.isFetched.value ? mergedHistoryRows.value : undefined
  )
  const monthlyHistoryRows = computed(() =>
    isCurrentPlatform.value && history.data.value !== undefined
      ? mergedHistoryRows.value
      : undefined
  )

  const calendarHistory = computed<CalendarHistory>(() => {
    if (!isCurrentPlatform.value) return 'idle'
    if (history.data.value !== undefined) return 'loaded'
    return history.isLoading.value ? 'loading' : 'idle'
  })
  const calendarHistoryStatus = computed<'idle' | 'loading' | 'success' | 'error'>(() => {
    if (!isCurrentPlatform.value) return 'idle'
    if (history.isFetching.value) return 'loading'
    if (history.error.value) return 'error'
    return history.data.value === undefined ? 'idle' : 'success'
  })
  const calendarHistoryError = computed(() =>
    isCurrentPlatform.value ? (history.error.value?.message ?? null) : null
  )
  const monthlyHistoryError = computed(() =>
    isCurrentPlatform.value
      ? (calendarHistoryError.value ?? calendarError.value ?? current.error.value?.message ?? null)
      : null
  )

  const loadCalendarHistory = () => {
    isHistoryRequested.value = true
    if (history.error.value) void history.refetch({ cancelRefetch: false })
  }
  const retryCalendarHistory = () => {
    isHistoryRequested.value = true
    const requests: Promise<unknown>[] = [history.refetch({ cancelRefetch: false })]
    if (calendar.error.value) requests.push(calendar.refetch({ cancelRefetch: false }))
    if (current.error.value) requests.push(current.refetch())
    return Promise.all(requests)
  }

  watchEffect(() => {
    calendarRefetchInterval.value = lacksDayBeforeLatest(mergedRecentRows.value)
      ? REFETCH_INTERVALS.DAILY_CALENDAR
      : false
  })
  watch(
    () => [platforms.value.join(','), current.data.value?.date] as const,
    ([platformsKey, date], [previousPlatforms, previousDate]) => {
      if (platformsKey === previousPlatforms && date && previousDate && date !== previousDate) {
        void calendar.refetch()
      }
    }
  )

  return {
    dailyCalendarRows,
    monthlyHistoryRows,
    monthlyHistoryError,
    calendarHistory,
    calendarHistoryStatus,
    calendarHistoryError,
    isCalendarHistoryFetching: computed(() => isCurrentPlatform.value && history.isFetching.value),
    calendarError,
    loadCalendarHistory,
    retryCalendarHistory,
  }
}
