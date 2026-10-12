import { computed, ref, watch, type Ref } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { watchDebounced } from '@vueuse/core'
import { diversificationService } from '../services/api'
import { getErrorMessage } from './use-diversification-result'
import type { AllocationDto } from '../models/generated/domain-models'

export function useHoldingsSearch(allocations: Ref<AllocationDto[]>, active: Ref<boolean>) {
  const query = ref('')
  const criteria = ref({ query: '', page: 0 })
  const revision = ref(0)
  const isDebouncing = computed(() => query.value !== criteria.value.query)

  watchDebounced(
    query,
    value => {
      criteria.value = { query: value, page: 0 }
    },
    { debounce: 300 }
  )

  watch(
    allocations,
    () => {
      criteria.value = { ...criteria.value, page: 0 }
      revision.value += 1
    },
    { deep: true, flush: 'sync' }
  )

  const result = useQuery({
    queryKey: computed(() => [
      'diversification-holdings',
      allocations.value,
      criteria.value,
      revision.value,
    ]),
    queryFn: () =>
      diversificationService.searchHoldings({ allocations: allocations.value, ...criteria.value }),
    enabled: computed(() => active.value && allocations.value.length > 0),
    placeholderData: keepPreviousData,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  const page = computed({
    get: () => criteria.value.page,
    set: (value: number) => {
      criteria.value = { ...criteria.value, page: value }
    },
  })

  return {
    query,
    page,
    data: result.data,
    isSearching: computed(() => active.value && (result.isFetching.value || isDebouncing.value)),
    error: computed(() => (result.error.value ? getErrorMessage(result.error.value) : '')),
  }
}
