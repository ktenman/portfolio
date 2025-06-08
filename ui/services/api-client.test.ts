import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ApiClient } from './api-client'
import { ApiError } from '../models/api-error'
import router from '../router'
import { APP_CONFIG } from '../constants/app-config'

vi.mock('../router', () => ({
  default: {
    push: vi.fn(),
  },
}))

describe('ApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    global.fetch = vi.fn()
    vi.spyOn(console, 'log').mockImplementation(() => {})
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  describe('request', () => {
    it('handles successful JSON response', async () => {
      const mockData = { id: 1, name: 'Test' }
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => mockData,
        headers: new Headers(),
      } as Response)

      const result = await ApiClient.request('/api/test')
      expect(result).toEqual(mockData)
    })

    it('handles 204 No Content response', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 204,
        type: 'basic',
        headers: new Headers(),
      } as Response)

      const result = await ApiClient.request('/api/test')
      expect(result).toBeUndefined()
    })

    it('handles redirect responses for non-calculator endpoints', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 302,
        type: 'opaqueredirect',
        headers: new Headers({ Location: '/login' }),
      } as Response)

      await expect(ApiClient.request('/api/test')).rejects.toThrow('Redirecting to login')
      expect(router.push).toHaveBeenCalledWith('/login')
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBe('1')
    })

    it('handles redirect responses for calculator endpoints', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 302,
        type: 'opaqueredirect',
        headers: new Headers({ Location: '/login' }),
      } as Response)

      const result = await ApiClient.request('/api/calculator/test')
      expect(result).toEqual({})
      expect(router.push).not.toHaveBeenCalled()
    })

    it('breaks redirect loop after max attempts', async () => {
      sessionStorage.setItem(
        APP_CONFIG.REDIRECT_COUNT_KEY,
        String(APP_CONFIG.MAX_REDIRECT_COUNT + 1)
      )

      await expect(ApiClient.request('/api/test')).rejects.toThrow(ApiError)
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBeNull()
    })

    it('returns empty object for calculator endpoints when max redirects exceeded', async () => {
      sessionStorage.setItem(
        APP_CONFIG.REDIRECT_COUNT_KEY,
        String(APP_CONFIG.MAX_REDIRECT_COUNT + 1)
      )

      const result = await ApiClient.request('/api/calculator/test')
      expect(result).toEqual({})
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBeNull()
    })

    it('handles API errors with error details', async () => {
      const errorResponse = {
        message: 'Validation failed',
        debugMessage: 'Field validation error',
        validationErrors: { name: 'Name is required' },
      }

      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
        type: 'basic',
        json: async () => errorResponse,
        headers: new Headers(),
      } as Response)

      try {
        await ApiClient.request('/api/test')
        expect.fail('Should have thrown an error')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        expect((error as ApiError).status).toBe(400)
        expect((error as ApiError).message).toBe('Validation failed')
        expect((error as ApiError).debugMessage).toBe('Field validation error')
        expect((error as ApiError).validationErrors).toEqual({ name: 'Name is required' })
      }
    })

    it('handles API errors without error details', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 500,
        type: 'basic',
        json: async () => {
          throw new Error('Invalid JSON')
        },
        headers: new Headers(),
        redirected: false,
        statusText: 'Internal Server Error',
        url: '',
        clone: vi.fn(),
        body: null,
        bodyUsed: false,
        arrayBuffer: vi.fn(),
        blob: vi.fn(),
        formData: vi.fn(),
        text: vi.fn(),
      } as unknown as Response)

      try {
        await ApiClient.request('/api/test')
        expect.fail('Should have thrown an error')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        expect((error as ApiError).status).toBe(500)
        expect((error as ApiError).message).toBe('API request failed')
        expect((error as ApiError).debugMessage).toBe('HTTP error! status: 500')
      }
    })

    it('handles JSON parsing errors', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => {
          throw new Error('Invalid JSON')
        },
        headers: new Headers(),
        redirected: false,
        statusText: 'OK',
        url: '',
        clone: vi.fn(),
        body: null,
        bodyUsed: false,
        arrayBuffer: vi.fn(),
        blob: vi.fn(),
        formData: vi.fn(),
        text: vi.fn(),
      } as unknown as Response)

      const result = await ApiClient.request('/api/test')
      expect(result).toEqual({})
    })

    it('handles external redirects', async () => {
      const mockReplace = vi.fn()
      Object.defineProperty(window, 'location', {
        value: { replace: mockReplace },
        writable: true,
      })

      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 302,
        type: 'opaqueredirect',
        headers: new Headers({ Location: 'https://external.com/login' }),
      } as Response)

      await expect(ApiClient.request('/api/test')).rejects.toThrow('Redirecting to login')
      expect(mockReplace).toHaveBeenCalledWith('https://external.com/login')
      expect(router.push).not.toHaveBeenCalled()
    })

    it('handles redirect without Location header using default', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: false,
        status: 302,
        type: 'opaqueredirect',
        headers: new Headers(), // No Location header
      } as Response)

      await expect(ApiClient.request('/api/test')).rejects.toThrow('Redirecting to login')
      expect(router.push).toHaveBeenCalledWith('/login')
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBe('1')
    })

    it('clears redirect count on successful response after redirects', async () => {
      // Set initial redirect count
      sessionStorage.setItem(APP_CONFIG.REDIRECT_COUNT_KEY, '2')

      const mockData = { id: 1, name: 'Test' }
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => mockData,
        headers: new Headers(),
      } as Response)

      const result = await ApiClient.request('/api/test')
      expect(result).toEqual(mockData)
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBeNull()
    })

    it('does not clear redirect count when count is already 0', async () => {
      // Ensure no redirect count is set initially
      sessionStorage.removeItem(APP_CONFIG.REDIRECT_COUNT_KEY)

      const mockData = { id: 1, name: 'Test' }
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => mockData,
        headers: new Headers(),
      } as Response)

      const result = await ApiClient.request('/api/test')
      expect(result).toEqual(mockData)
      expect(sessionStorage.getItem(APP_CONFIG.REDIRECT_COUNT_KEY)).toBeNull()
    })
  })

  describe('HTTP methods', () => {
    it('makes GET requests', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => ({ data: 'test' }),
        headers: new Headers(),
      } as Response)

      await ApiClient.get('/api/test')
      expect(global.fetch).toHaveBeenCalledWith('/api/test', { redirect: 'manual' })
    })

    it('makes POST requests with data', async () => {
      const postData = { name: 'Test' }
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 201,
        type: 'basic',
        json: async () => ({ id: 1, ...postData }),
        headers: new Headers(),
      } as Response)

      await ApiClient.post('/api/test', postData)
      expect(global.fetch).toHaveBeenCalledWith('/api/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(postData),
        redirect: 'manual',
      })
    })

    it('makes PUT requests with data', async () => {
      const putData = { id: 1, name: 'Updated' }
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 200,
        type: 'basic',
        json: async () => putData,
        headers: new Headers(),
      } as Response)

      await ApiClient.put('/api/test/1', putData)
      expect(global.fetch).toHaveBeenCalledWith('/api/test/1', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(putData),
        redirect: 'manual',
      })
    })

    it('makes DELETE requests', async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce({
        ok: true,
        status: 204,
        type: 'basic',
        headers: new Headers(),
      } as Response)

      await ApiClient.delete('/api/test/1')
      expect(global.fetch).toHaveBeenCalledWith('/api/test/1', {
        method: 'DELETE',
        redirect: 'manual',
      })
    })
  })
})
