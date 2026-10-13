import { computed, ref, watch, type Ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { useToast } from './use-toast'

export type SummaryInterval = 'daily' | 'monthly'

export const useStoredSummaryInterval = () => {
  const stored = useLocalStorage<string>(STORAGE_KEYS.SUMMARY_INTERVAL, 'daily', {
    listenToStorageChanges: false,
    flush: 'sync',
  })
  if (stored.value !== 'daily' && stored.value !== 'monthly') stored.value = 'daily'
  return computed<SummaryInterval>({
    get: () => (stored.value === 'monthly' ? 'monthly' : 'daily'),
    set: value => {
      stored.value = value
    },
  })
}

export const useSummaryInterval = (
  interval: Ref<SummaryInterval>,
  platforms: Ref<string[]>,
  history: {
    status: Ref<'idle' | 'loading' | 'success' | 'error'>
    rows: Ref<PortfolioSummaryDto[] | undefined>
    span: Ref<string>
  }
) => {
  const toast = useToast()
  const displayInterval = ref<SummaryInterval>(interval.value)
  const announcement = ref('')
  const announceSwitch = ref(false)
  const busy = computed(() => interval.value === 'monthly' && !history.rows.value)

  watch(
    () => platforms.value.join(','),
    () => {
      displayInterval.value = interval.value
      announceSwitch.value = false
      announcement.value = ''
    },
    { flush: 'sync' }
  )

  watch(
    [interval, history.status, history.rows],
    () => {
      if (interval.value !== 'monthly') return
      if (history.rows.value) {
        displayInterval.value = 'monthly'
        if (announceSwitch.value) announcement.value = `Monthly profit ${history.span.value}`.trim()
        announceSwitch.value = false
        return
      }
      if (history.status.value !== 'error') return
      interval.value = 'daily'
      displayInterval.value = 'daily'
      announceSwitch.value = false
      toast.error('Could not load the monthly history. Press Monthly to try again.')
    },
    { immediate: true, flush: 'post' }
  )

  const selectInterval = (value: SummaryInterval) => {
    if (value === interval.value) return
    announcement.value = ''
    announceSwitch.value = value === 'monthly'
    if (value === 'daily') displayInterval.value = 'daily'
    interval.value = value
  }

  return { interval, displayInterval, busy, announcement, selectInterval }
}
