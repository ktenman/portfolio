import {ApiError} from '../models/api-error'

export class ApiClient {
  static async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(url, {
      ...options,
      redirect: 'manual', // This prevents automatic redirect following
    })

    if (response.type === 'opaqueredirect' || response.status === 302) {
      window.location.href = response.headers.get('Location') || '/login'
      window.location.reload()
      throw new Error('Redirecting and reloading page')
    }

    if (!response.ok && response.status !== 302) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData?.message ?? `API request failed`,
        errorData?.debugMessage ?? `HTTP error! status: ${response.status}`,
        errorData?.validationErrors ?? {}
      )
    }

    return response.json()
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
