import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { httpClient } from './http-client'

describe('httpClient', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const jsonResponse = (body: unknown, status = 200, statusText = 'OK') =>
    new Response(JSON.stringify(body), { status, statusText })

  const requestedUrl = () => fetchMock.mock.calls[0][0] as string
  const requestedInit = () => fetchMock.mock.calls[0][1] as RequestInit

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should prefix requests with the api base path', async () => {
    await httpClient.get('/build-info')
    expect(requestedUrl()).toBe('/api/build-info')
  })

  it('should resolve to the parsed response body', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ artifact: 'pörtfölio' }))
    await expect(httpClient.get('/build-info')).resolves.toEqual({ artifact: 'pörtfölio' })
  })

  it('should abort the request with the configured timeout', async () => {
    const timeout = vi.spyOn(AbortSignal, 'timeout')
    await httpClient.post('/portfolio-summary/recalculate', undefined, { timeout: 60000 })
    expect(timeout).toHaveBeenCalledWith(60000)
  })

  it('should abort the request with the default timeout when none is given', async () => {
    const timeout = vi.spyOn(AbortSignal, 'timeout')
    await httpClient.get('/build-info')
    expect(timeout).toHaveBeenCalledWith(10000)
  })

  describe('params serializer', () => {
    it('should send an array as a single comma separated param', async () => {
      await httpClient.get('/portfolio-summary/current', {
        params: { range: '2Y', platforms: ['LHV', 'BINANCE', 'AVIVA'] },
      })
      expect(requestedUrl()).toBe(
        '/api/portfolio-summary/current?range=2Y&platforms=LHV,BINANCE,AVIVA'
      )
    })

    it('should omit params that are undefined or null', async () => {
      await httpClient.get('/instruments', {
        params: { page: 0, size: undefined, platforms: null },
      })
      expect(requestedUrl()).toBe('/api/instruments?page=0')
    })

    it('should encode reserved characters in scalar params', async () => {
      await httpClient.get('/logos/search', { params: { name: 'Kärcher & Sons' } })
      expect(requestedUrl()).toBe('/api/logos/search?name=K%C3%A4rcher+%26+Sons')
    })

    it('should omit the query string when every param is empty', async () => {
      await httpClient.get('/transactions', { params: {} })
      expect(requestedUrl()).toBe('/api/transactions')
    })
  })

  describe('request body', () => {
    it('should send the payload as json', async () => {
      await httpClient.post('/transactions', { platform: 'LHV' })
      expect(requestedInit().body).toBe('{"platform":"LHV"}')
    })

    it('should declare a json content type when a payload is sent', async () => {
      await httpClient.put('/instruments/1', { name: 'Kärcher' })
      expect(requestedInit().headers).toEqual({ 'Content-Type': 'application/json' })
    })

    it('should send no body when no payload is given', async () => {
      await httpClient.post('/instruments/refresh-prices')
      expect(requestedInit().body).toBeUndefined()
    })
  })

  describe('response handling', () => {
    it('should resolve to undefined for a 204 response', async () => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
      await expect(httpClient.get('/transactions/1')).resolves.toBeUndefined()
    })

    it('should resolve to undefined for an empty 200 response', async () => {
      fetchMock.mockResolvedValue(new Response('', { status: 200 }))
      await expect(
        httpClient.post('/logos/prefetch', { holdingUuids: [] })
      ).resolves.toBeUndefined()
    })
  })

  describe('error handling', () => {
    const originalLocation = window.location

    beforeEach(() => {
      Object.defineProperty(window, 'location', {
        value: { ...originalLocation, href: '', pathname: '/instruments' },
        writable: true,
      })
    })

    afterEach(() => {
      Object.defineProperty(window, 'location', {
        value: originalLocation,
        writable: true,
      })
    })

    it('should redirect to OAuth start on 401 error', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 401, 'Unauthorized'))
      await expect(httpClient.get('/test')).rejects.toThrow()
      expect(window.location.href).toBe('/oauth2/start?rd=%2Finstruments')
    })

    it('should throw ApiError with response data', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(
          {
            message: 'Validation failed',
            debugMessage: 'Field X is required',
            validationErrors: { field: 'Required' },
          },
          400,
          'Bad Request'
        )
      )
      await expect(httpClient.get('/test')).rejects.toMatchObject({
        name: 'ApiError',
        status: 400,
        message: 'Validation failed',
        debugMessage: 'Field X is required',
        validationErrors: { field: 'Required' },
      })
    })

    it('should use fallback values when response data is missing', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 500, 'Internal Server Error'))
      await expect(httpClient.get('/test-endpoint')).rejects.toMatchObject({
        name: 'ApiError',
        status: 500,
        message: 'Internal Server Error',
        debugMessage: 'Request failed: /test-endpoint',
        validationErrors: {},
      })
    })

    it('should use fallback values when the error body is not json', async () => {
      fetchMock.mockResolvedValue(
        new Response('<html>oops</html>', { status: 502, statusText: 'Bad Gateway' })
      )
      await expect(httpClient.get('/test')).rejects.toMatchObject({
        name: 'ApiError',
        status: 502,
        message: 'Bad Gateway',
        debugMessage: 'Request failed: /test',
      })
    })

    it('should handle errors without a response', async () => {
      fetchMock.mockRejectedValue(new TypeError('Network Error'))
      await expect(httpClient.get('/test')).rejects.toMatchObject({
        name: 'ApiError',
        status: 500,
        message: 'Network Error',
        debugMessage: 'Request failed: /test',
        validationErrors: {},
      })
    })

    it('should handle 401 and still throw ApiError', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ message: 'Session expired' }, 401, 'Unauthorized'))
      await expect(httpClient.get('/protected')).rejects.toMatchObject({
        name: 'ApiError',
        status: 401,
        message: 'Session expired',
      })
      expect(window.location.href).toContain('/oauth2/start?rd=')
    })
  })
})
