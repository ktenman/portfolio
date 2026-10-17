import { computed, ref, watch, type Ref, type WritableComputedRef } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import { NOT_ENOUGH_HISTORY, type MonthlyProfit } from '../services/monthly-profit'
import type { HistoryStatus } from './use-summary-calendar-query'
import { useToast } from './use-toast'

export type SummaryInterval = 'daily' | 'monthly'
export type IntervalView = 'daily' | 'pending' | 'monthly'

interface IntervalSource {
  model: Readonly<Ref<MonthlyProfit | null | undefined>>
  status: Readonly<Ref<HistoryStatus>>
  platforms: Readonly<Ref<string[]>>
}

const LOAD_FAILED = 'Could not load the monthly history. Press Monthly to try again.'

export const useStoredInterval = (): WritableComputedRef<SummaryInterval> => {
  const stored = useLocalStorage<string>(STORAGE_KEYS.SUMMARY_INTERVAL, 'daily', {
    listenToStorageChanges: false,
  })
  return computed<SummaryInterval>({
    get: () => (stored.value === 'monthly' ? 'monthly' : 'daily'),
    set: value => {
      stored.value = value
    },
  })
}

export const useSummaryInterval = (
  interval: WritableComputedRef<SummaryInterval>,
  { model, status, platforms }: IntervalSource
) => {
  const toast = useToast()
  const isPending = ref(false)
  const announcement = ref('')

  const view = computed<IntervalView>(() => {
    if (interval.value === 'daily') return 'daily'
    return isPending.value ? 'pending' : 'monthly'
  })

  const announce = (months: MonthlyProfit | null) => {
    announcement.value = months ? `Monthly profit ${months.span}` : NOT_ENOUGH_HISTORY
  }

  const choose = (next: SummaryInterval) => {
    if (next === interval.value) return
    announcement.value = ''
    interval.value = next
    isPending.value = next === 'monthly' && model.value === undefined
    if (next === 'monthly' && model.value !== undefined) announce(model.value)
  }

  watch(model, months => {
    if (!isPending.value || months === undefined) return
    isPending.value = false
    announce(months)
  })

  watch(platforms, () => {
    isPending.value = false
  })

  watch(status, current => {
    if (current !== 'error' || interval.value === 'daily' || model.value !== undefined) return
    isPending.value = false
    interval.value = 'daily'
    toast.error(LOAD_FAILED)
  })

  return { view, announcement, choose }
}
