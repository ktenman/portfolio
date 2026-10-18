import { computed, ref, watch, type Ref, type WritableComputedRef } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import { NOT_ENOUGH_HISTORY, type MonthlyProfit } from '../services/monthly-profit'
import { useToast } from './use-toast'

export type SummaryInterval = 'daily' | 'monthly'
type IntervalView = 'daily' | 'pending' | 'monthly'

interface IntervalSource {
  model: Readonly<Ref<MonthlyProfit | null | undefined>>
  error: Readonly<Ref<Error | null>>
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
  { model, error, platforms }: IntervalSource
) => {
  const toast = useToast()
  const isPending = ref(false)
  const announcement = ref('')

  const view = computed<IntervalView>(() => {
    if (interval.value === 'daily') return 'daily'
    return isPending.value ? 'pending' : 'monthly'
  })

  const choose = (next: SummaryInterval) => {
    if (next === interval.value) return
    announcement.value = ''
    interval.value = next
    isPending.value = next === 'monthly'
  }

  watch(
    () => (isPending.value ? model.value : undefined),
    months => {
      if (months === undefined) return
      isPending.value = false
      announcement.value = months ? `Monthly profit ${months.span}` : NOT_ENOUGH_HISTORY
    }
  )

  watch(platforms, () => {
    isPending.value = false
  })

  watch(error, current => {
    if (!current || interval.value === 'daily' || model.value !== undefined) return
    isPending.value = false
    interval.value = 'daily'
    toast.error(LOAD_FAILED)
  })

  return { view, announcement, choose }
}
