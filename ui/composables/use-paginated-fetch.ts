import { ref, Ref, computed, shallowRef } from 'vue'
import { ApiError } from '../models/api-error'
import { Page } from '../models/page'

export interface PaginatedFetchOptions {
  initialPage?: number
  initialSize?: number
  onError?: (error: ApiError) => void
  onSuccess?: (data: any) => void
}

export interface UsePaginatedFetchResult<T> {
  items: Ref<T[]>
  page: Ref<Page<T> | null>
  error: Ref<ApiError | null>
  loading: Ref<boolean>
  currentPage: Ref<number>
  pageSize: Ref<number>
  totalPages: Ref<number>
  totalElements: Ref<number>
  hasNext: Ref<boolean>
  hasPrevious: Ref<boolean>
  fetchPage: (page: number, size?: number) => Promise<void>
  nextPage: () => Promise<void>
  previousPage: () => Promise<void>
  refresh: () => Promise<void>
}

export function usePaginatedFetch<T>(
  fetcher: (page: number, size: number) => Promise<Page<T>>,
  options: PaginatedFetchOptions = {}
): UsePaginatedFetchResult<T> {
  const { initialPage = 0, initialSize = 20, onError, onSuccess } = options

  const items = shallowRef<T[]>([])
  const page = shallowRef<Page<T> | null>(null)
  const error = ref<ApiError | null>(null)
  const loading = ref(false)
  const currentPage = ref(initialPage)
  const pageSize = ref(initialSize)

  const totalPages = computed(() => page.value?.totalPages || 0)
  const totalElements = computed(() => page.value?.totalElements || 0)
  const hasNext = computed(() => currentPage.value < totalPages.value - 1)
  const hasPrevious = computed(() => currentPage.value > 0)

  const fetchPage = async (pageNum: number, size?: number) => {
    loading.value = true
    error.value = null

    if (size !== undefined) {
      pageSize.value = size
    }

    try {
      const result = await fetcher(pageNum, pageSize.value)
      page.value = result
      items.value = result.content
      currentPage.value = pageNum
      onSuccess?.(result)
    } catch (e) {
      const apiError = e as ApiError
      error.value = apiError
      onError?.(apiError)
    } finally {
      loading.value = false
    }
  }

  const nextPage = async () => {
    if (hasNext.value) {
      await fetchPage(currentPage.value + 1)
    }
  }

  const previousPage = async () => {
    if (hasPrevious.value) {
      await fetchPage(currentPage.value - 1)
    }
  }

  const refresh = async () => {
    await fetchPage(currentPage.value)
  }

  return {
    items,
    page,
    error,
    loading,
    currentPage,
    pageSize,
    totalPages,
    totalElements,
    hasNext,
    hasPrevious,
    fetchPage,
    nextPage,
    previousPage,
    refresh,
  }
}
