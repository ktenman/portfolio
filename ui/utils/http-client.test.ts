import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { ApiError } from '../models/api-error'

describe('httpClient', () => {
  let mockCreate: any
  let mockInterceptors: any

  const createAxiosError = (
    status: number,
    data: any = {},
    message = 'Error',
    url = '/test'
  ): AxiosError => ({
    response: {
      status,
      data,
      statusText: message,
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    },
    message,
    config: { url } as InternalAxiosRequestConfig,
    isAxiosError: true,
    toJSON: () => ({}),
    name: 'AxiosError',
  })

  beforeEach(async () => {
    vi.resetModules()

    mockInterceptors = {
      response: {
        use: vi.fn(),
      },
    }

    mockCreate = vi.fn().mockReturnValue({
      interceptors: mockInterceptors,
    })

    vi.doMock('axios', () => ({
      default: {
        create: mockCreate,
      },
    }))

    await import('./http-client')
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.resetModules()
  })

  it('should create axios instance with correct config', () => {
    expect(mockCreate).toHaveBeenCalledWith({
      baseURL: '/api',
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    })
  })

  describe('response interceptor', () => {
    let successHandler: any
    let errorHandler: any

    beforeEach(() => {
      ;[[successHandler, errorHandler]] = mockInterceptors.response.use.mock.calls
    })

    describe('success handler', () => {
      it('should return response as-is for non-204 status', () => {
        const response = { status: 200, data: { message: 'success' } }
        const result = successHandler(response)
        expect(result).toEqual(response)
      })

      it('should set data to undefined for 204 status', () => {
        const response = { status: 204, data: 'some data' }
        const result = successHandler(response)
        expect(result).toEqual({ status: 204, data: undefined })
      })
    })

    describe('error handler', () => {
      const originalLocation = window.location

      beforeEach(() => {
        Object.defineProperty(window, 'location', {
          value: { ...originalLocation, href: '' },
          writable: true,
        })
      })

      afterEach(() => {
        Object.defineProperty(window, 'location', {
          value: originalLocation,
          writable: true,
        })
      })

      it('should redirect to login on 401 error', () => {
        const error = createAxiosError(401, {}, 'Unauthorized')

        expect(() => errorHandler(error)).toThrow()
        expect(window.location.href).toBe('/login')
      })

      it('should throw ApiError with response data', () => {
        const errorData = {
          message: 'Validation failed',
          debugMessage: 'Field X is required',
          validationErrors: { field: 'Required' },
        }

        const error = createAxiosError(400, errorData, 'Bad Request')

        try {
          errorHandler(error)
        } catch (e) {
          expect(e).toBeInstanceOf(Error)
          const apiError = e as ApiError
          expect(apiError.name).toBe('ApiError')
          expect(apiError.status).toBe(400)
          expect(apiError.message).toBe('Validation failed')
          expect(apiError.debugMessage).toBe('Field X is required')
          expect(apiError.validationErrors).toEqual({ field: 'Required' })
        }
      })

      it('should use fallback values when response data is missing', () => {
        const error = createAxiosError(500, {}, 'Internal Server Error', '/test-endpoint')

        try {
          errorHandler(error)
        } catch (e) {
          expect(e).toBeInstanceOf(Error)
          const apiError = e as ApiError
          expect(apiError.name).toBe('ApiError')
          expect(apiError.status).toBe(500)
          expect(apiError.message).toBe('Internal Server Error')
          expect(apiError.debugMessage).toBe('Request failed: /test-endpoint')
          expect(apiError.validationErrors).toEqual({})
        }
      })

      it('should handle errors without response', () => {
        const error: AxiosError = {
          message: 'Network Error',
          config: { url: '/test' } as InternalAxiosRequestConfig,
          isAxiosError: true,
          toJSON: () => ({}),
          name: 'AxiosError',
          response: undefined,
        }

        try {
          errorHandler(error)
        } catch (e) {
          expect(e).toBeInstanceOf(Error)
          const apiError = e as ApiError
          expect(apiError.name).toBe('ApiError')
          expect(apiError.status).toBe(500)
          expect(apiError.message).toBe('Network Error')
          expect(apiError.debugMessage).toBe('Request failed: /test')
          expect(apiError.validationErrors).toEqual({})
        }
      })

      it('should handle 401 and still throw ApiError', () => {
        const error = createAxiosError(
          401,
          { message: 'Session expired' },
          'Unauthorized',
          '/protected'
        )

        try {
          errorHandler(error)
        } catch (e) {
          expect(window.location.href).toBe('/login')
          expect(e).toBeInstanceOf(Error)
          const apiError = e as ApiError
          expect(apiError.name).toBe('ApiError')
          expect(apiError.status).toBe(401)
          expect(apiError.message).toBe('Session expired')
        }
      })
    })
  })
})
