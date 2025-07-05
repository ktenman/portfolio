import axios, { AxiosError } from 'axios'
import { ApiError } from '../models/api-error'
import { ApiErrorResponse } from '../models/api-error-response'

export const httpClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

httpClient.interceptors.response.use(
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
