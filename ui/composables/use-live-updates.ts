import { computed, watch } from 'vue'
import { useDocumentVisibility, useEventListener, useEventSource, useTimeoutFn } from '@vueuse/core'
import { useQueryClient, type QueryKey } from '@tanstack/vue-query'
import { useAuthState } from './use-auth-state'
import { liveUpdatesService } from '../services/api'
import { LiveUpdate } from '../models/generated/domain-models'

const RETRY_DELAY = 15 * 1000

const SILENCE_TIMEOUT = 3 * 60 * 1000

const QUERIES: Record<LiveUpdate, QueryKey[]> = {
  [LiveUpdate.PRICES]: [
    ['instruments'],
    ['portfolio-summary', 'current'],
    ['portfolio-summary', 'range-change'],
  ],
  [LiveUpdate.SUMMARY]: [
    ['portfolio-summary', 'current'],
    ['portfolio-summary', 'intraday'],
    ['portfolio-summary', 'range-change'],
    ['instruments'],
  ],
}

export function useLiveUpdates() {
  const queryClient = useQueryClient()
  const { isAuthenticated } = useAuthState()
  const visibility = useDocumentVisibility()
  const url = computed(() =>
    isAuthenticated.value && visibility.value === 'visible'
      ? liveUpdatesService.streamUrl
      : undefined
  )
  const { eventSource, error, open } = useEventSource(url)
  const { start: retry } = useTimeoutFn(open, RETRY_DELAY, { immediate: false })
  const { start: arm } = useTimeoutFn(open, SILENCE_TIMEOUT)
  watch(eventSource, () => arm())
  watch(error, () => {
    if (eventSource.value?.readyState === EventSource.CLOSED) retry()
  })
  Object.values(LiveUpdate).forEach(topic =>
    useEventListener(eventSource, topic, () => {
      arm()
      QUERIES[topic].forEach(queryKey => queryClient.invalidateQueries({ queryKey }))
    })
  )
}
