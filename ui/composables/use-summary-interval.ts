import { computed, ref, watch, type Ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import type { HistoryStatus } from './use-summary-calendar-query'
import { useToast } from './use-toast'

export type SummaryInterval = 'daily' | 'monthly'
export type SummaryView = SummaryInterval | 'placeholder'

const isInterval = (value: string): value is SummaryInterval =>
  value === 'daily' || value === 'monthly'

export const useSummaryInterval = (
  history: { status: Ref<HistoryStatus>; load: () => void },
  platforms: Ref<string[]>
) => {
  const toast = useToast()
  const stored = useLocalStorage<string>(STORAGE_KEYS.SUMMARY_INTERVAL, 'daily', {
    listenToStorageChanges: false,
    flush: 'sync',
  })
  if (!isInterval(stored.value)) stored.value = 'daily'
  const interval = stored as Ref<SummaryInterval>
  const isUserSwitch = ref(false)
  const isDailyRetained = ref(false)

  const view = computed<SummaryView>(() => {
    if (interval.value === 'daily') return 'daily'
    if (history.status.value === 'success') return 'monthly'
    return isDailyRetained.value ? 'daily' : 'placeholder'
  })

  const isLoadingMonths = computed(() => interval.value === 'monthly' && view.value === 'daily')

  const select = (value: SummaryInterval) => {
    if (value === interval.value) return
    interval.value = value
    if (value === 'daily') return
    isUserSwitch.value = true
    isDailyRetained.value = true
    history.load()
  }

  const restore = () => {
    if (interval.value !== 'monthly') return
    isUserSwitch.value = false
    isDailyRetained.value = false
    history.load()
  }

  restore()
  watch(platforms, restore)

  watch(history.status, (status, previous) => {
    if (status !== 'error' || previous !== 'loading' || interval.value !== 'monthly') return
    toast.error('Could not load the monthly history. Press Monthly to try again.')
    interval.value = 'daily'
  })

  return { interval, view, isLoadingMonths, isUserSwitch, select }
}
