import { ref, Ref, shallowRef } from 'vue'

interface UseApiOptions {
  immediate?: boolean
}

type ApiFunction<T, TArgs extends any[]> = (...args: TArgs) => Promise<T>

interface UseApiReturn<T, TArgs extends any[]> {
  data: Ref<T | null>
  error: Ref<Error | null>
  isLoading: Ref<boolean>
  execute: (...args: TArgs) => Promise<T | null>
}

export function useApi<T, TArgs extends any[] = any[]>(
  apiCall: ApiFunction<T, TArgs>,
  options: UseApiOptions = {}
): UseApiReturn<T, TArgs> {
  const data = shallowRef<T | null>(null)
  const error = ref<Error | null>(null)
  const isLoading = ref(false)

  const execute = async (...args: TArgs): Promise<T | null> => {
    isLoading.value = true
    error.value = null

    try {
      const result = await apiCall(...args)
      data.value = result
      return result
    } catch (e) {
      const errorMessage = e instanceof Error ? e : new Error(String(e))
      error.value = errorMessage
      console.error('API call failed:', errorMessage)
      return null
    } finally {
      isLoading.value = false
    }
  }

  if (options.immediate) {
    execute(...([] as unknown as TArgs))
  }

  return {
    data,
    error,
    isLoading,
    execute,
  }
}
