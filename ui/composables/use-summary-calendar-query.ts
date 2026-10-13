import { computed, ref, watch, watchEffect, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { REFETCH_INTERVALS } from '../constants'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'
import { portfolioSummaryService } from '../services/api'
import { CALENDAR_RANGE, lacksDayBeforeLatest } from '../services/daily-profit-calendar'
import {
  mergeHistoricalWithCurrent,
  mergeSummariesByDate,
  sortSummariesByDateAsc,
} from '../services/summary-aggregator'
import type { CalendarHistory } from './use-daily-profit-calendar'

export type HistoryStatus = 'idle' | 'loading' | 'success' | 'error'

interface SummaryCalendarSources {
  isAuthenticated: Ref<boolean>
  platformsKey: Ref<string[]>
  activePlatforms: Ref<string[] | undefined>
  currentSummary: Ref<PortfolioSummaryDto | undefined>
}

export const useSummaryCalendarQuery = ({
  isAuthenticated,
  platformsKey,
  activePlatforms,
  currentSummary,
}: SummaryCalendarSources) => {
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
    isError: isCalendarHistoryError,
    errorUpdateCount: calendarHistoryFailures,
  } = useQuery({
    queryKey: ['portfolio-summary', 'daily-calendar-history', platformsKey],
    queryFn: () => portfolioSummaryService.getSeries(TimeRange.MAX, activePlatforms.value),
    enabled: computed(() => isAuthenticated.value && isHistoryRequested.value),
    staleTime: Infinity,
    retry: 1,
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

  const historyStatus = computed<HistoryStatus>(() => {
    if (calendarHistoryRows.value) return 'success'
    if (isLoadingCalendarHistory.value) return 'loading'
    return isCalendarHistoryError.value ? 'error' : 'idle'
  })

  const isHistoryStale = computed(() => !!calendarHistoryRows.value && isCalendarHistoryError.value)

  const monthlyRows = computed(() =>
    calendarHistoryRows.value
      ? mergeSummariesByDate(
          calendarHistoryRows.value,
          calendarSeries.value ?? [],
          currentSummary.value ? [currentSummary.value] : []
        )
      : undefined
  )

  const loadCalendarHistory = () => {
    isHistoryRequested.value = true
  }

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
    historyStatus,
    monthlyRows,
    isHistoryStale,
  }
}
