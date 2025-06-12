import { ApiError } from '../models/api-error.ts'

interface ErrorResponse {
  message?: string
  debugMessage?: string
  validationErrors?: Record<string, string>
}

export class ApiClient {
  private static readonly REDIRECT_COUNT_KEY = 'redirectCount'
  private static readonly MAX_REDIRECT_ATTEMPTS = 3
  private static readonly DEFAULT_LOGIN_URL = '/login'
  private static readonly CALCULATOR_ENDPOINT_PATTERN = '/calculator'

  private static getCurrentRedirectCount(): number {
    return parseInt(sessionStorage.getItem(this.REDIRECT_COUNT_KEY) || '0')
  }

  private static incrementRedirectCount(): void {
    const currentCount = this.getCurrentRedirectCount()
    sessionStorage.setItem(this.REDIRECT_COUNT_KEY, (currentCount + 1).toString())
  }

  private static clearRedirectCount(): void {
    sessionStorage.removeItem(this.REDIRECT_COUNT_KEY)
  }

  private static isRedirectLoopDetected(): boolean {
    return this.getCurrentRedirectCount() > this.MAX_REDIRECT_ATTEMPTS
  }

  private static isCalculatorEndpoint(url: string): boolean {
    return url.includes(this.CALCULATOR_ENDPOINT_PATTERN)
  }

  private static handleRedirectLoopForCalculator<T>(): T {
    this.clearRedirectCount()
    return {} as T
  }

  private static handleRedirectLoop(): never {
    this.clearRedirectCount()
    throw new ApiError(
      401,
      'Authentication error',
      'Unable to authenticate after multiple attempts',
      {}
    )
  }

  private static handleRedirectResponse(response: Response): never {
    this.incrementRedirectCount()
    const redirectUrl = response.headers.get('Location') || this.DEFAULT_LOGIN_URL
    window.location.replace(redirectUrl)
    throw new Error('Redirecting to login')
  }

  private static isRedirectResponse(response: Response): boolean {
    return response.type === 'opaqueredirect' || response.status === 302
  }

  private static async parseErrorResponse(response: Response): Promise<ErrorResponse> {
    try {
      return await response.json()
    } catch {
      return {}
    }
  }

  private static createApiError(response: Response, errorData: ErrorResponse): ApiError {
    return new ApiError(
      response.status,
      errorData.message ?? 'API request failed',
      errorData.debugMessage ?? `HTTP error! status: ${response.status}`,
      errorData.validationErrors ?? {}
    )
  }

  private static isNoContentResponse(response: Response): boolean {
    return response.status === 204
  }

  private static async parseJsonResponse<T>(response: Response): Promise<T> {
    try {
      return await response.json()
    } catch (e) {
      console.error('Error parsing JSON response:', e)
      return {} as T
    }
  }

  static async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const isCalculator = this.isCalculatorEndpoint(url)

    if (this.isRedirectLoopDetected()) {
      return isCalculator ? this.handleRedirectLoopForCalculator<T>() : this.handleRedirectLoop()
    }

    const response = await fetch(url, {
      ...options,
      redirect: 'manual',
    })

    if (this.isRedirectResponse(response)) {
      if (isCalculator) {
        return {} as T
      }
      this.handleRedirectResponse(response)
    }

    if (this.getCurrentRedirectCount() > 0) {
      this.clearRedirectCount()
    }

    if (!response.ok) {
      const errorData = await this.parseErrorResponse(response)
      throw this.createApiError(response, errorData)
    }

    if (this.isNoContentResponse(response)) {
      return undefined as unknown as T
    }

    return this.parseJsonResponse<T>(response)
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
