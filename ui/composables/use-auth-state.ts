import { ref, readonly } from 'vue'
import { httpClient } from '../utils/http-client'
import { API_ENDPOINTS } from '../constants'

const isAuthenticated = ref(false)
const isAuthChecking = ref(true)
const authCheckPromise = ref<Promise<boolean> | null>(null)

async function checkAuth(): Promise<boolean> {
  if (authCheckPromise.value) {
    return authCheckPromise.value
  }

  authCheckPromise.value = (async () => {
    try {
      await httpClient.get(API_ENDPOINTS.BUILD_INFO)
      isAuthenticated.value = true
      return true
    } catch {
      isAuthenticated.value = false
      return false
    } finally {
      isAuthChecking.value = false
    }
  })()

  return authCheckPromise.value
}

export function useAuthState() {
  return {
    isAuthenticated: readonly(isAuthenticated),
    isAuthChecking: readonly(isAuthChecking),
    checkAuth,
  }
}
