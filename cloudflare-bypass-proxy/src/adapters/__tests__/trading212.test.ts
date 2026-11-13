import request from 'supertest'
import express from 'express'
import { listen } from '../../__tests__/listen'
import { trading212Adapter } from '../trading212'

const { executeCurl } = vi.hoisted(() => ({ executeCurl: vi.fn() }))

vi.mock('../../utils/curl-executor', () => ({ executeCurl }))

describe('Trading212 Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(trading212Adapter.path, trading212Adapter.handler)
    vi.clearAllMocks()
  })

  it('should return 400 if tickers parameter is missing', async () => {
    const response = await request(await listen(app)).get('/prices')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Missing tickers parameter' })
  })

  it('should post the tickers to the last-trade endpoint and return its prices', async () => {
    executeCurl.mockResolvedValue({
      stdout: '{"VUAAm_EQ":133.35,"BNKEp_EQ":4.75}',
      duration: 100,
      statusCode: 200,
      contentType: 'application/json',
    })

    const response = await request(await listen(app)).get('/prices?tickers=BNKEp_EQ,VUAAm_EQ')

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ VUAAm_EQ: 133.35, BNKEp_EQ: 4.75 })
    expect(executeCurl).toHaveBeenCalledWith({
      url: 'https://live.services.trading212.com/instrumentarium/v1/prices/last-trade',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"tickers":["BNKEp_EQ","VUAAm_EQ"],"extended":false}',
      timeout: 10000,
      maxBuffer: 1024 * 1024,
    })
  })

  it('should answer with the upstream status when Trading212 returns an error', async () => {
    executeCurl.mockResolvedValue({ stdout: '', duration: 100, statusCode: 404, contentType: '' })

    const response = await request(await listen(app)).get('/prices?tickers=BNKEp_EQ')

    expect(response.status).toBe(404)
    expect(response.body).toHaveProperty('upstreamStatus', 404)
  })

  it('should name the upstream URL when Trading212 returns an error', async () => {
    executeCurl.mockResolvedValue({ stdout: '', duration: 100, statusCode: 404, contentType: '' })

    const response = await request(await listen(app)).get('/prices?tickers=BNKEp_EQ')

    expect(response.body.message).toContain(
      'https://live.services.trading212.com/instrumentarium/v1/prices/last-trade'
    )
  })

  it('should handle curl execution errors', async () => {
    executeCurl.mockRejectedValue(new Error('Network timeout'))

    const response = await request(await listen(app)).get('/prices?tickers=AAPL')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error', 'Failed to fetch data')
    expect(response.body).toHaveProperty('message')
  })

  it('should handle invalid JSON response', async () => {
    executeCurl.mockResolvedValue({
      stdout: 'invalid json',
      duration: 100,
      statusCode: 200,
      contentType: 'application/json',
    })

    const response = await request(await listen(app)).get('/prices?tickers=AAPL')

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
