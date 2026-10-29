import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import { TimeRange } from '../models/generated/domain-models'

export const TIME_RANGES = Object.values(TimeRange)

export const DEFAULT_CHART_RANGE = TimeRange.ONE_MONTH

export type RangeUnit = 'D' | 'W' | 'M' | 'Y'

export const RANGE_UNITS: { value: RangeUnit; label: string; noun: string }[] = [
  { value: 'D', label: 'Days', noun: 'day' },
  { value: 'W', label: 'Weeks', noun: 'week' },
  { value: 'M', label: 'Months', noun: 'month' },
  { value: 'Y', label: 'Years', noun: 'year' },
]

export const RANGE_LENGTHS = [1, 2, 3, 4, 5, 6]

export const SHORTLIST: TimeRange[] = [
  TimeRange.ONE_DAY,
  TimeRange.ONE_WEEK,
  TimeRange.ONE_MONTH,
  TimeRange.SIX_MONTHS,
  TimeRange.YTD,
  TimeRange.ONE_YEAR,
  TimeRange.FIVE_YEARS,
  TimeRange.MAX,
]

const PERIOD = /^([1-6])([DWMY])$/

export function splitRange(range: TimeRange): { unit: RangeUnit; length: number } | null {
  const match = PERIOD.exec(range)
  if (!match) return null
  return { unit: match[2] as RangeUnit, length: Number(match[1]) }
}

export const joinRange = (unit: RangeUnit, length: number) => `${length}${unit}` as TimeRange

export function describeRange(range: TimeRange): string {
  if (range === TimeRange.YTD) return 'since 1 January'
  const period = splitRange(range)
  if (!period) return 'since the first buy'
  const noun = RANGE_UNITS.find(unit => unit.value === period.unit)?.noun
  return period.length === 1 ? `in the past ${noun}` : `in the past ${period.length} ${noun}s`
}

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
