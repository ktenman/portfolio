import request from 'supertest'
import express from 'express'
import { lightyearAdapter } from '../lightyear'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('Lightyear Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(lightyearAdapter.path, ...(lightyearAdapter.middleware || []), lightyearAdapter.handler)
    jest.clearAllMocks()
  })

  it('should return 400 if path parameter is missing', async () => {
    const response = await request(app).get('/lightyear/fetch')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Missing path parameter' })
  })

  it('should successfully fetch price data with base64 encoded path', async () => {
    const mockResponse = {
      timestamp: '2025-11-14T16:00:00Z',
      price: 36.26,
      change: 0.31,
      changePercent: 0.0087,
      currency: 'EUR',
    }

    executeCurl.mockResolvedValue({
      stdout: JSON.stringify(mockResponse),
      duration: 150,
    })

    const path = '/v1/market-data/1ef27f9a-bde6-6dda-a873-3946ca86bd5c/price'
    const response = await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockResponse)

    const expectedEncodedPath = Buffer.from(path).toString('base64').replace(/=+$/, '')
    expect(executeCurl).toHaveBeenCalledWith({
      url: `https://lightyear.com/fetch?path=${expectedEncodedPath}&withAPIKey=true`,
      timeout: 10000,
      maxBuffer: 1024 * 1024,
      headers: {
        'user-agent':
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36',
        referer: 'https://lightyear.com/',
        accept: '*/*',
        'accept-language': 'en-US,en;q=0.9',
      },
    })
  })

  it('should encode path without padding characters', async () => {
    executeCurl.mockResolvedValue({
      stdout: JSON.stringify({ price: 100 }),
      duration: 100,
    })

    const path = '/v1/market-data/test/price'
    await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

    const callArgs = executeCurl.mock.calls[0][0]
    const encodedPath = callArgs.url.match(/path=([^&]+)/)[1]

    expect(encodedPath).not.toContain('=')
  })

  it('should successfully fetch chart data with query parameters', async () => {
    const mockChartData = [
      { timestamp: '2025-01-01', price: 100 },
      { timestamp: '2025-01-02', price: 101 },
    ]

    executeCurl.mockResolvedValue({
      stdout: JSON.stringify(mockChartData),
      duration: 200,
    })

    const path = '/v1/market-data/1f02fdcc-38f9-67b8-ad1b-a71ae2564bd4/chart?range=max'
    const response = await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockChartData)
  })

  it('should handle curl execution errors', async () => {
    executeCurl.mockRejectedValue(new Error('Cloudflare blocked'))

    const response = await request(app).get('/lightyear/fetch?path=/v1/test')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error', 'Failed to fetch data')
    expect(response.body).toHaveProperty('message')
  })

  it('should include all required headers for Cloudflare bypass', async () => {
    executeCurl.mockResolvedValue({
      stdout: JSON.stringify({ price: 50 }),
      duration: 100,
    })

    await request(app).get('/lightyear/fetch?path=/v1/test')

    const callArgs = executeCurl.mock.calls[0][0]
    expect(callArgs.headers).toHaveProperty('user-agent')
    expect(callArgs.headers).toHaveProperty('referer', 'https://lightyear.com/')
    expect(callArgs.headers).toHaveProperty('accept', '*/*')
    expect(callArgs.headers).toHaveProperty('accept-language', 'en-US,en;q=0.9')
  })

  it('should have correct adapter configuration', () => {
    expect(lightyearAdapter.path).toBe('/lightyear/fetch')
    expect(lightyearAdapter.method).toBe('GET')
    expect(lightyearAdapter.middleware).toBeDefined()
    expect(Array.isArray(lightyearAdapter.middleware)).toBe(true)
    expect(lightyearAdapter.middleware?.length).toBeGreaterThan(0)
  })

  it('should sanitize log input for security', async () => {
    const consoleSpy = jest.spyOn(console, 'log').mockImplementation()

    executeCurl.mockResolvedValue({
      stdout: JSON.stringify({ price: 50 }),
      duration: 100,
    })

    await request(app).get('/lightyear/fetch?path=/v1/test')

    expect(consoleSpy).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})
