import { ref, Ref, unref, watch, shallowRef } from 'vue'
import { ApiError } from '../models/api-error'

export interface FetchOptions {
  immediate?: boolean
  onError?: (error: ApiError) => void
  onSuccess?: (data: any) => void
  dependencies?: Ref[]
}

export interface UseFetchResult<T> {
  data: Ref<T | null>
  error: Ref<ApiError | null>
  loading: Ref<boolean>
  execute: () => Promise<void>
  refresh: () => Promise<void>
}

export function useDataFetch<T>(
  fetcher: () => Promise<T>,
  options: FetchOptions = {}
): UseFetchResult<T> {
  const { immediate = false, onError, onSuccess, dependencies = [] } = options

  const data = shallowRef<T | null>(null)
  const error = ref<ApiError | null>(null)
  const loading = ref(false)

  const execute = async () => {
    loading.value = true
    error.value = null

    try {
      const result = await fetcher()
      data.value = result
      onSuccess?.(result)
    } catch (e) {
      const apiError = e as ApiError
      error.value = apiError
      onError?.(apiError)
    } finally {
      loading.value = false
    }
  }

  const refresh = () => execute()

  if (dependencies.length > 0) {
    watch(
      dependencies.map(dep => () => unref(dep)),
      () => execute(),
      { immediate }
    )
  } else if (immediate) {
    execute()
  }

  return {
    data,
    error,
    loading,
    execute,
    refresh,
  }
}
