import { computed, type MaybeRefOrGetter, toValue } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { radioService } from '../services/api'
import { useAuthState } from './use-auth-state'

export const useRadioRecordings = () => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: ['radio'],
    queryFn: radioService.recordings,
    enabled: isAuthenticated,
  })
}

export const useRadioRecording = (name: MaybeRefOrGetter<string>) => {
  const query = useRadioRecordings()
  const recording = computed(() => query.data.value?.find(r => r.name === toValue(name)) ?? null)
  return { ...query, recording }
}
