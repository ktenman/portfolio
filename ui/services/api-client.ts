import { ApiError } from '../models/api-error.ts'
import router from '../router/index.ts'
import { APP_CONFIG } from '../constants/app-config.ts'

interface ErrorResponse {
  message?: string
  debugMessage?: string
  validationErrors?: Record<string, string>
}

export class ApiClient {
  static async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const redirectCount = parseInt(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY) || '0')
    const isCalculatorEndpoint = url.includes('/calculator')

    console.log(`Request to ${url}, redirect count: ${redirectCount}`)

    if (redirectCount > APP_CONFIG.MAX_REDIRECT_COUNT) {
      console.error('Too many redirects detected, breaking the loop')
      sessionStorage.removeItem(APP_CONFIG.REDIRECT_COUNT_KEY)

      if (isCalculatorEndpoint) {
        console.log('Returning empty data for calculator endpoint')
        return {} as T
      }

      throw new ApiError(
        401,
        'Authentication error',
        'Unable to authenticate after multiple attempts',
        {}
      )
    }

    const response = await fetch(url, {
      ...options,
      redirect: 'manual',
    })

    console.log(`Response for ${url}: status=${response.status}, type=${response.type}`)

    if (response.type === 'opaqueredirect' || response.status === 302) {
      if (isCalculatorEndpoint) {
        console.log(
          'Calculator endpoint accessed without authentication, continuing with empty response'
        )
        return {} as T
      }

      sessionStorage.setItem(APP_CONFIG.REDIRECT_COUNT_KEY, (redirectCount + 1).toString())
      const redirectUrl = response.headers.get('Location') || '/login'
      console.log(`Redirecting to: ${redirectUrl}`)

      if (redirectUrl.startsWith('/')) {
        await router.push(redirectUrl)
      } else {
        window.location.replace(redirectUrl)
      }
      throw new Error('Redirecting to login')
    }

    if (redirectCount > 0) {
      sessionStorage.removeItem(APP_CONFIG.REDIRECT_COUNT_KEY)
    }

    if (!response.ok) {
      let errorData: ErrorResponse = {}
      try {
        errorData = await response.json()
      } catch {}

      throw new ApiError(
        response.status,
        errorData.message ?? 'API request failed',
        errorData.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData.validationErrors ?? {}
      )
    }

    if (response.status === 204) {
      return undefined as unknown as T
    }

    try {
      return await response.json()
    } catch (e) {
      console.error('Error parsing JSON response:', e)
      return {} as T
    }
  }

  static get<T>(url: string): Promise<T> {
    return this.request<T>(url)
  }

  static post<T>(url: string, data: any): Promise<T> {
    return this.request<T>(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    })
  }

  static put<T>(url: string, data: any): Promise<T> {
    return this.request<T>(url, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    })
  }

  static delete(url: string): Promise<void> {
    return this.request(url, {
      method: 'DELETE',
    })
  }
}
