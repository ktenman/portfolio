import { ref, shallowRef, Ref } from 'vue'

interface UseApiOptions {
  immediate?: boolean
}

interface UseApiReturn<T> {
  data: Ref<T | null>
  error: Ref<Error | null>
  isLoading: Ref<boolean>
  execute: (...args: any[]) => Promise<T | null>
}

export function useApi<T>(
  apiCall: (...args: any[]) => Promise<T>,
  options: UseApiOptions = {}
): UseApiReturn<T> {
  const data = shallowRef<T | null>(null)
  const error = ref<Error | null>(null)
  const isLoading = ref(false)

  const execute = async (...args: any[]): Promise<T | null> => {
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
    execute()
  }

  return {
    data,
    error,
    isLoading,
    execute,
  }
}
