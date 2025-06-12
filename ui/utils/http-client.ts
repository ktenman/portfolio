import axios, { AxiosError } from 'axios'
import { ApiError } from '../models/api-error'

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
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      window.location.href = '/login'
    }

    const data = error.response?.data as any
    throw new ApiError(
      error.response?.status ?? 500,
      data?.message ?? error.message,
      data?.debugMessage ?? `Request failed: ${error.config?.url}`,
      data?.validationErrors ?? {}
    )
  }
)
