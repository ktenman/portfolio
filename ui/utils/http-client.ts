import axios, { AxiosError, AxiosRequestConfig } from 'axios'
import { ApiError } from '../models/api-error'
import { ApiErrorResponse } from '../models/api-error-response'

const axiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  paramsSerializer: {
    indexes: null,
  },
})

axiosInstance.interceptors.response.use(
  response => {
    if (response.status === 204) {
      return { ...response, data: undefined }
    }
    return response
  },
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response?.status === 401) {
      window.location.href = '/oauth2/start?rd=' + encodeURIComponent(window.location.pathname)
    }

    const data = error.response?.data
    throw new ApiError(
      error.response?.status ?? 500,
      data?.message ?? error.message,
      data?.debugMessage ?? `Request failed: ${error.config?.url}`,
      data?.validationErrors ?? {}
    )
  }
)

function ensureJsonResponse<T>(data: unknown, url?: string): T {
  if (typeof data === 'string' && data.trimStart().startsWith('<')) {
    throw new ApiError(502, 'Received HTML instead of JSON', `Non-JSON response from: ${url}`, {})
  }
  return data as T
}

export const httpClient = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    axiosInstance.get<T>(url, config).then(res => ensureJsonResponse<T>(res.data, url)),

  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    axiosInstance.post<T>(url, data, config).then(res => ensureJsonResponse<T>(res.data, url)),

  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    axiosInstance.put<T>(url, data, config).then(res => ensureJsonResponse<T>(res.data, url)),

  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    axiosInstance.delete<T>(url, config).then(res => ensureJsonResponse<T>(res.data, url)),
}
