import { computed, ref, watch, type Ref } from 'vue'
import { keepPreviousData, useInfiniteQuery } from '@tanstack/vue-query'
import { watchDebounced } from '@vueuse/core'
import { diversificationService } from '../services/api'
import { getErrorMessage } from './use-diversification-result'
import type { AllocationDto } from '../models/generated/domain-models'

export function useHoldingsSearch(allocations: Ref<AllocationDto[]>, active: Ref<boolean>) {
  const query = ref('')
  const term = ref('')
  const revision = ref(0)
  const isDebouncing = computed(() => query.value !== term.value)

  watchDebounced(
    query,
    value => {
      term.value = value
    },
    { debounce: 300 }
  )

  watch(
    allocations,
    () => {
      revision.value += 1
    },
    { deep: true, flush: 'sync' }
  )

  const result = useInfiniteQuery({
    queryKey: computed(() => [
      'diversification-holdings',
      allocations.value,
      term.value,
      revision.value,
    ]),
    queryFn: ({ pageParam }) =>
      diversificationService.searchHoldings({
        allocations: allocations.value,
        query: term.value,
        page: pageParam,
      }),
    initialPageParam: 0,
    getNextPageParam: last => (last.number + 1 < last.totalPages ? last.number + 1 : undefined),
    enabled: computed(() => active.value && allocations.value.length > 0),
    placeholderData: keepPreviousData,
    staleTime: 'static',
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  return {
    query,
    holdings: computed(() => result.data.value?.pages.flatMap(page => page.content)),
    total: computed(() => result.data.value?.pages[0]?.totalElements ?? 0),
    hasMore: result.hasNextPage,
    moreFailed: result.isFetchNextPageError,
    loadingMore: result.isFetchingNextPage,
    loadMore: () => {
      if (result.hasNextPage.value) result.fetchNextPage({ cancelRefetch: false })
    },
    isSearching: computed(
      () =>
        active.value &&
        ((result.isFetching.value && !result.isFetchingNextPage.value) || isDebouncing.value)
    ),
    error: computed(() =>
      result.error.value && !result.isFetchNextPageError.value
        ? getErrorMessage(result.error.value)
        : ''
    ),
  }
}
