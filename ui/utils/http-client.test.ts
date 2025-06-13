import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AxiosError } from 'axios'

describe('httpClient', () => {
  let mockCreate: any
  let mockInterceptors: any

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
        delete (window as any).location
        ;(window as any).location = { ...originalLocation, href: '' }
      })

      afterEach(() => {
        ;(window as any).location = originalLocation
      })

      it('should redirect to login on 401 error', () => {
        const error: Partial<AxiosError> = {
          response: { status: 401, data: {} } as any,
          message: 'Unauthorized',
          config: { url: '/test' } as any,
        }

        expect(() => errorHandler(error)).toThrow()
        expect(window.location.href).toBe('/login')
      })

      it('should throw ApiError with response data', () => {
        const errorData = {
          message: 'Validation failed',
          debugMessage: 'Field X is required',
          validationErrors: { field: 'Required' },
        }

        const error: Partial<AxiosError> = {
          response: {
            status: 400,
            data: errorData,
          } as any,
          message: 'Bad Request',
          config: { url: '/test' } as any,
        }

        try {
          errorHandler(error)
        } catch (e: any) {
          expect(e.name).toBe('ApiError')
          expect(e.status).toBe(400)
          expect(e.message).toBe('Validation failed')
          expect(e.debugMessage).toBe('Field X is required')
          expect(e.validationErrors).toEqual({ field: 'Required' })
        }
      })

      it('should use fallback values when response data is missing', () => {
        const error: Partial<AxiosError> = {
          response: {
            status: 500,
            data: {},
          } as any,
          message: 'Internal Server Error',
          config: { url: '/test-endpoint' } as any,
        }

        try {
          errorHandler(error)
        } catch (e: any) {
          expect(e.name).toBe('ApiError')
          expect(e.status).toBe(500)
          expect(e.message).toBe('Internal Server Error')
          expect(e.debugMessage).toBe('Request failed: /test-endpoint')
          expect(e.validationErrors).toEqual({})
        }
      })

      it('should handle errors without response', () => {
        const error: Partial<AxiosError> = {
          message: 'Network Error',
          config: { url: '/test' } as any,
        }

        try {
          errorHandler(error)
        } catch (e: any) {
          expect(e.name).toBe('ApiError')
          expect(e.status).toBe(500)
          expect(e.message).toBe('Network Error')
          expect(e.debugMessage).toBe('Request failed: /test')
          expect(e.validationErrors).toEqual({})
        }
      })

      it('should handle 401 and still throw ApiError', () => {
        const error: Partial<AxiosError> = {
          response: {
            status: 401,
            data: { message: 'Session expired' },
          } as any,
          message: 'Unauthorized',
          config: { url: '/protected' } as any,
        }

        try {
          errorHandler(error)
        } catch (e: any) {
          expect(window.location.href).toBe('/login')
          expect(e.name).toBe('ApiError')
          expect(e.status).toBe(401)
          expect(e.message).toBe('Session expired')
        }
      })
    })
  })
})
