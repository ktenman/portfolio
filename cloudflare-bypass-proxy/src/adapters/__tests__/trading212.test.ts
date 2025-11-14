import request from 'supertest'
import express from 'express'
import { trading212Adapter } from '../trading212'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('Trading212 Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(trading212Adapter.path, trading212Adapter.handler)
    jest.clearAllMocks()
  })

  it('should return 400 if tickers parameter is missing', async () => {
    const response = await request(app).get('/prices')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Missing tickers parameter' })
  })

  it('should successfully fetch prices for valid tickers', async () => {
    const mockResponse = {
      AAPL: { price: 150.25, currency: 'USD' },
      GOOGL: { price: 2800.5, currency: 'USD' },
    }

    executeCurl.mockResolvedValue({
      stdout: JSON.stringify(mockResponse),
      duration: 100,
    })

    const response = await request(app).get('/prices?tickers=AAPL,GOOGL')

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockResponse)
    expect(executeCurl).toHaveBeenCalledWith({
      url: 'https://live.services.trading212.com/public-instrument-cache/v1/prices?tickers=AAPL,GOOGL',
      timeout: 10000,
      maxBuffer: 1024 * 1024,
      headers: undefined,
    })
  })

  it('should handle curl execution errors', async () => {
    executeCurl.mockRejectedValue(new Error('Network timeout'))

    const response = await request(app).get('/prices?tickers=AAPL')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error', 'Failed to fetch data')
    expect(response.body).toHaveProperty('message')
  })

  it('should handle invalid JSON response', async () => {
    executeCurl.mockResolvedValue({
      stdout: 'invalid json',
      duration: 100,
    })

    const response = await request(app).get('/prices?tickers=AAPL')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error')
  })

  it('should have correct adapter configuration', () => {
    expect(trading212Adapter.path).toBe('/prices')
    expect(trading212Adapter.method).toBe('GET')
    expect(trading212Adapter.middleware).toBeDefined()
    expect(Array.isArray(trading212Adapter.middleware)).toBe(true)
    expect(trading212Adapter.middleware?.length).toBeGreaterThan(0)
  })
})
