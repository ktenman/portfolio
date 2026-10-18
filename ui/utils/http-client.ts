import { ApiError } from '../models/api-error'
import { ApiErrorResponse } from '../models/api-error-response'

interface RequestConfig {
  params?: Record<string, unknown>
  timeout?: number
}

const BASE_URL = '/api'
const DEFAULT_TIMEOUT = 10000

const serializeParams = (params: Record<string, unknown>): string => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return
    search.append(key, Array.isArray(value) ? value.join(',') : String(value))
  })
  return search.toString().replace(/%2C/g, ',')
}

const buildUrl = (url: string, params?: Record<string, unknown>): string => {
  const query = params ? serializeParams(params) : ''
  return query ? `${BASE_URL}${url}?${query}` : `${BASE_URL}${url}`
}

const parseBody = async (response: Response): Promise<unknown> => {
  const text = await response.text()
  return text ? JSON.parse(text) : undefined
}

const fail = (status: number, message: string, url: string, data?: ApiErrorResponse): never => {
  if (status === 401) {
    window.location.href = '/oauth2/start?rd=' + encodeURIComponent(window.location.pathname)
  }
  throw new ApiError(
    status,
    data?.message ?? message,
    data?.debugMessage ?? `Request failed: ${url}`,
    data?.validationErrors ?? {}
  )
}

const request = async <T>(
  method: string,
  url: string,
  body?: unknown,
  config?: RequestConfig
): Promise<T> => {
  const response = await fetch(buildUrl(url, config?.params), {
    method,
    signal: AbortSignal.timeout(config?.timeout ?? DEFAULT_TIMEOUT),
    ...(body === undefined
      ? {}
      : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
  }).catch((error: Error) => fail(500, error.message, url))

  if (!response.ok) {
    const data = (await parseBody(response).catch(() => undefined)) as ApiErrorResponse | undefined
    return fail(response.status, response.statusText, url, data)
  }
  return (await parseBody(response)) as T
}

export const httpClient = {
  get: <T>(url: string, config?: RequestConfig): Promise<T> =>
    request<T>('GET', url, undefined, config),

  post: <T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> =>
    request<T>('POST', url, data, config),

  put: <T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> =>
    request<T>('PUT', url, data, config),

  delete: <T>(url: string, config?: RequestConfig): Promise<T> =>
    request<T>('DELETE', url, undefined, config),
}
