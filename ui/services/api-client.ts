import { ApiError } from '../models/api-error.ts'

interface ErrorResponse {
  message?: string
  debugMessage?: string
  validationErrors?: Record<string, string>
}

export class ApiClient {
  static async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    // Add a redirect counter to detect loops
    const redirectCount = parseInt(sessionStorage.getItem('redirectCount') || '0')

    // Check if this is a calculator endpoint
    const isCalculatorEndpoint = url.includes('/calculator')

    console.log(`Request to ${url}, redirect count: ${redirectCount}`)

    // Break infinite loops after a few redirects
    if (redirectCount > 3) {
      console.error('Too many redirects detected, breaking the loop')
      sessionStorage.removeItem('redirectCount')

      // For calculator endpoint, return empty data rather than redirecting
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
      // Special handling for calculator endpoint
      if (isCalculatorEndpoint) {
        console.log(
          'Calculator endpoint accessed without authentication, continuing with empty response'
        )
        return {} as T // Return empty result instead of redirecting
      }

      // For other endpoints, increment redirect counter and redirect
      sessionStorage.setItem('redirectCount', (redirectCount + 1).toString())
      const redirectUrl = response.headers.get('Location') || '/login'
      console.log(`Redirecting to: ${redirectUrl}`)

      // Use replace instead of reload to avoid adding to browser history
      window.location.replace(redirectUrl)
      throw new Error('Redirecting to login')
    }

    // Reset redirect counter on successful requests
    if (redirectCount > 0) {
      sessionStorage.removeItem('redirectCount')
    }

    // Handle non-successful responses
    if (!response.ok) {
      let errorData: ErrorResponse = {}
      try {
        errorData = await response.json()
      } catch {
        // If parsing fails, use default error message
      }

      throw new ApiError(
        response.status,
        errorData.message ?? 'API request failed',
        errorData.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData.validationErrors ?? {}
      )
    }

    // Return void for 204 responses
    if (response.status === 204) {
      return undefined as unknown as T
    }

    // Parse JSON for other successful responses
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
