import { computed, type MaybeRefOrGetter, ref, toValue, watch, type WatchSource } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { keepPreviousData, useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { RecordingSummary } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { poll } from '../utils/radio'
import { useAuthState } from './use-auth-state'

export const useRadioRecordings = () => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: ['radio'],
    queryFn: radioService.recordings,
    enabled: isAuthenticated,
    refetchInterval: query => poll(query.state.data ?? []),
  })
}

export const useRadioProgrammes = () => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: ['radio-programmes'],
    queryFn: radioService.programmes,
    enabled: isAuthenticated,
  })
}

export const useRadioStation = () => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: ['radio-station'],
    queryFn: radioService.station,
    enabled: isAuthenticated,
    refetchInterval: 60_000,
  })
}

export const useRadioDay = (date: MaybeRefOrGetter<string | null>) => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: computed(() => ['radio-day', toValue(date)]),
    queryFn: () => radioService.day(toValue(date)),
    enabled: isAuthenticated,
    refetchInterval: 60_000,
  })
}

export const useRadioSearch = (query: MaybeRefOrGetter<string>) => {
  const { isAuthenticated } = useAuthState()
  const queryClient = useQueryClient()
  const queryKey = computed(() => ['radio', 'search', toValue(query)] as const)
  const result = useInfiniteQuery({
    queryKey,
    queryFn: ({ queryKey: [, , q], pageParam }) => radioService.search(q, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: last => last.next,
    enabled: isAuthenticated,
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    retry: false,
    shallow: true,
  })
  const loadMore = () => {
    if (!result.hasNextPage.value) return
    const key = queryKey.value
    const stale = result.isStale.value && !result.isFetching.value
    result.fetchNextPage({ cancelRefetch: false }).then(() => {
      if (stale) queryClient.invalidateQueries({ queryKey: key, exact: true, refetchType: 'none' })
    })
  }
  return { ...result, loadMore }
}

export const useRadioRecording = (name: MaybeRefOrGetter<string>) => {
  const query = useRadioRecordings()
  const recording = computed(() => query.data.value?.find(r => r.name === toValue(name)) ?? null)
  return { ...query, recording }
}

const useTranscribed = <T>(
  recording: MaybeRefOrGetter<RecordingSummary>,
  key: string,
  fetch: (name: string) => Promise<T>
) => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: computed(() => {
      const { name, timeline, audible } = toValue(recording)
      return ['radio', name, key, timeline, audible]
    }),
    queryFn: () => fetch(toValue(recording).name),
    enabled: computed(() => isAuthenticated.value && toValue(recording).transcribed),
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    shallow: true,
  })
}

export const useRadioCuts = (recording: MaybeRefOrGetter<RecordingSummary>) =>
  useTranscribed(recording, 'cuts', radioService.cuts)

export const useRadioTranscript = (recording: MaybeRefOrGetter<RecordingSummary>) =>
  useTranscribed(recording, 'transcript', radioService.transcript)

export const useElapsed = (source: WatchSource) => {
  const now = useNow({ scheduler: tick => useIntervalFn(tick, 1000) })
  const since = ref(Date.now())
  watch(source, () => (since.value = Date.now()))
  return computed(() => (now.value.getTime() - since.value) / 1000)
}
