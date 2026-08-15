import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import { TimeRange } from '../models/generated/domain-models'

export const TIME_RANGES = Object.values(TimeRange)

export const DEFAULT_CHART_RANGE = TimeRange.ONE_MONTH

function useStoredRange(key: string, fallback: TimeRange) {
  const stored = useLocalStorage<TimeRange>(key, fallback)

  return computed<TimeRange>({
    get: () => (TIME_RANGES.includes(stored.value) ? stored.value : fallback),
    set: value => {
      stored.value = value
    },
  })
}

export function useChartRange() {
  return useStoredRange(STORAGE_KEYS.SUMMARY_CHART_RANGE, DEFAULT_CHART_RANGE)
}

export function usePriceChangePeriod() {
  return useStoredRange(STORAGE_KEYS.PRICE_CHANGE_PERIOD, TimeRange.ONE_DAY)
}
