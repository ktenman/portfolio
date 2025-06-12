import { ApiError } from '../models/api-error'

interface ErrorResponse {
  message?: string
  debugMessage?: string
  validationErrors?: Record<string, string>
}

export interface ApiClientConfig {
  baseUrl?: string
  onUnauthorized?: () => void
  headers?: Record<string, string>
}

export class ApiClient {
  private baseUrl: string
  private defaultHeaders: Record<string, string>
  private onUnauthorized?: () => void

  constructor(config: ApiClientConfig = {}) {
    this.baseUrl = config.baseUrl || ''
    this.defaultHeaders = config.headers || {}
    this.onUnauthorized = config.onUnauthorized
  }

  async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const fullUrl = `${this.baseUrl}${url}`

    const response = await fetch(fullUrl, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...this.defaultHeaders,
        ...options.headers,
      },
    })

    if (response.status === 401 && this.onUnauthorized) {
      this.onUnauthorized()
      throw new ApiError(401, 'Unauthorized', 'Authentication required', {})
    }

    if (response.status === 204) {
      return undefined as unknown as T
    }

    if (!response.ok) {
      const errorData = await this.parseErrorResponse(response)
      throw this.createApiError(response, errorData)
    }

    try {
      return await response.json()
    } catch (e) {
      console.error('Failed to parse JSON response:', e)
      throw new ApiError(500, 'Invalid Response', 'Failed to parse server response', {})
    }
  }

  get<T>(url: string): Promise<T> {
    return this.request<T>(url)
  }

  post<T>(url: string, data: unknown): Promise<T> {
    return this.request<T>(url, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  put<T>(url: string, data: unknown): Promise<T> {
    return this.request<T>(url, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  }

  delete(url: string): Promise<void> {
    return this.request(url, {
      method: 'DELETE',
    })
  }

  private async parseErrorResponse(response: Response): Promise<ErrorResponse> {
    try {
      return await response.json()
    } catch {
      return {}
    }
  }

  private createApiError(response: Response, errorData: ErrorResponse): ApiError {
    return new ApiError(
      response.status,
      errorData.message ?? 'API request failed',
      errorData.debugMessage ?? `HTTP error! status: ${response.status}`,
      errorData.validationErrors ?? {}
    )
  }
}

export const apiClient = new ApiClient()
