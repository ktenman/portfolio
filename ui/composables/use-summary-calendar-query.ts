import { computed, ref, watch, watchEffect, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/api'
import {
  mergeHistoricalWithCurrent,
  mergeSummariesByDate,
  sortSummariesByDateAsc,
} from '../services/summary-aggregator'
import { CALENDAR_RANGE, lacksDayBeforeLatest } from '../services/daily-profit-calendar'
import { REFETCH_INTERVALS } from '../constants'
import type { CalendarHistory } from './use-daily-profit-calendar'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'

export type HistoryStatus = 'idle' | 'loading' | 'success' | 'error'

interface SummaryCalendarOptions {
  platformsKey: Readonly<Ref<string[]>>
  activePlatforms: Readonly<Ref<string[] | undefined>>
  isAuthenticated: Readonly<Ref<boolean>>
  currentSummary: Readonly<Ref<PortfolioSummaryDto | undefined>>
  isMonthly: Readonly<Ref<boolean>>
}

export function useSummaryCalendarQuery({
  platformsKey,
  activePlatforms,
  isAuthenticated,
  currentSummary,
  isMonthly,
}: SummaryCalendarOptions) {
  const calendarRefetchInterval = ref<number | false>(false)

  const {
    data: calendarSeries,
    isFetched: isCalendarFetched,
    isSuccess: isCalendarSuccess,
    error: calendarQueryError,
    refetch: refetchCalendar,
  } = useQuery({
    queryKey: ['portfolio-summary', 'daily-calendar', platformsKey],
    queryFn: () => portfolioSummaryService.getSeries(CALENDAR_RANGE, activePlatforms.value),
    refetchInterval: calendarRefetchInterval,
    enabled: isAuthenticated,
    shallow: true,
  })

  const calendarError = ref<string | null>(null)

  watchEffect(() => {
    if (calendarQueryError.value) {
      calendarError.value = calendarQueryError.value.message
      return
    }
    if (isCalendarSuccess.value) calendarError.value = null
  })

  const mergedCalendarRows = computed(() =>
    mergeHistoricalWithCurrent(calendarSeries.value ?? [], currentSummary.value)
  )

  const isHistoryRequested = ref(false)

  watch(
    platformsKey,
    () => {
      isHistoryRequested.value = false
    },
    { flush: 'sync' }
  )

  const {
    data: calendarHistoryRows,
    isLoading: isLoadingCalendarHistory,
    isFetching: isFetchingCalendarHistory,
    error: calendarHistoryError,
    errorUpdateCount: calendarHistoryFailures,
    refetch: retryHistory,
  } = useQuery({
    queryKey: ['portfolio-summary', 'daily-calendar-history', platformsKey],
    queryFn: () => portfolioSummaryService.getSeries(TimeRange.MAX, activePlatforms.value),
    enabled: computed(() => isAuthenticated.value && (isHistoryRequested.value || isMonthly.value)),
    staleTime: Infinity,
    retry: 1,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    shallow: true,
  })

  watch(calendarHistoryFailures, () => {
    isHistoryRequested.value = false
  })

  const dailyCalendarRows = computed(() => {
    if (!isCalendarFetched.value) return undefined
    const rows = mergedCalendarRows.value
    const earliest = sortSummariesByDateAsc(rows)[0]?.date
    const older = (calendarHistoryRows.value ?? []).filter(row => !earliest || row.date < earliest)
    return [...older, ...rows]
  })

  const calendarHistory = computed<CalendarHistory>(() => {
    if (calendarHistoryRows.value) return 'loaded'
    return isLoadingCalendarHistory.value ? 'loading' : 'idle'
  })

  const loadCalendarHistory = () => {
    isHistoryRequested.value = true
  }

  const monthlyRows = computed(() => {
    if (!calendarHistoryRows.value) return undefined
    const current = currentSummary.value
    return mergeSummariesByDate(
      calendarHistoryRows.value,
      calendarSeries.value ?? [],
      current ? [current] : []
    )
  })

  const historyStatus = computed<HistoryStatus>(() => {
    if (calendarHistoryError.value) return 'error'
    if (calendarHistoryRows.value) return 'success'
    return isFetchingCalendarHistory.value ? 'loading' : 'idle'
  })

  watchEffect(() => {
    calendarRefetchInterval.value = lacksDayBeforeLatest(mergedCalendarRows.value)
      ? REFETCH_INTERVALS.DAILY_CALENDAR
      : false
  })

  watch(
    () => currentSummary.value?.date,
    (date, previousDate) => {
      if (date && previousDate) refetchCalendar()
    }
  )

  return {
    dailyCalendarRows,
    calendarHistory,
    loadCalendarHistory,
    calendarError,
    monthlyRows,
    historyStatus,
    retryHistory,
  }
}
