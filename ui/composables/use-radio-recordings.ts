import { computed, type MaybeRefOrGetter, toValue } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
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
    placeholderData: keepPreviousData,
  })
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
    queryKey: computed(() => ['radio', toValue(recording).name, key]),
    queryFn: () => fetch(toValue(recording).name),
    enabled: computed(() => isAuthenticated.value && toValue(recording).transcribed),
    staleTime: Infinity,
    shallow: true,
  })
}

export const useRadioCuts = (recording: MaybeRefOrGetter<RecordingSummary>) =>
  useTranscribed(recording, 'cuts', radioService.cuts)

export const useRadioTranscript = (recording: MaybeRefOrGetter<RecordingSummary>) =>
  useTranscribed(recording, 'transcript', radioService.transcript)
