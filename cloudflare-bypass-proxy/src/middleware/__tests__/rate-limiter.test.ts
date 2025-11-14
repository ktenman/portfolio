import { createRateLimiter } from '../rate-limiter'
import express from 'express'
import request from 'supertest'

describe('Rate Limiter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
  })

  it('should use default rate limit configuration (60 requests per minute)', async () => {
    app.get('/test', createRateLimiter(), (req, res) => {
      res.json({ success: true })
    })

    const response = await request(app).get('/test')

    expect(response.status).toBe(200)
    expect(response.headers['ratelimit-limit']).toBe('60')
    expect(response.headers['ratelimit-remaining']).toBeDefined()
  })

  it('should allow custom rate limit configuration', async () => {
    app.get('/test', createRateLimiter({ max: 10, windowMs: 30 * 1000 }), (req, res) => {
      res.json({ success: true })
    })

    const response = await request(app).get('/test')

    expect(response.status).toBe(200)
    expect(response.headers['ratelimit-limit']).toBe('10')
  })

  it('should allow partial configuration override', async () => {
    app.get('/test', createRateLimiter({ max: 30 }), (req, res) => {
      res.json({ success: true })
    })

    const response = await request(app).get('/test')

    expect(response.status).toBe(200)
    expect(response.headers['ratelimit-limit']).toBe('30')
  })

  it('should return 429 when rate limit is exceeded', async () => {
    app.get('/test', createRateLimiter({ max: 2, windowMs: 60000 }), (req, res) => {
      res.json({ success: true })
    })

    await request(app).get('/test')
    await request(app).get('/test')
    const response = await request(app).get('/test')

    expect(response.status).toBe(429)
    expect(response.body).toHaveProperty('error')
    expect(response.body.error).toContain('Too many requests')
  })

  it('should include standard rate limit headers', async () => {
    app.get('/test', createRateLimiter(), (req, res) => {
      res.json({ success: true })
    })

    const response = await request(app).get('/test')

    expect(response.headers).toHaveProperty('ratelimit-limit')
    expect(response.headers).toHaveProperty('ratelimit-remaining')
    expect(response.headers).toHaveProperty('ratelimit-reset')
  })

  it('should not include legacy headers', async () => {
    app.get('/test', createRateLimiter(), (req, res) => {
      res.json({ success: true })
    })

    const response = await request(app).get('/test')

    expect(response.headers).not.toHaveProperty('x-ratelimit-limit')
    expect(response.headers).not.toHaveProperty('x-ratelimit-remaining')
  })

  it('should track rate limits per endpoint separately', async () => {
    app.get('/endpoint1', createRateLimiter({ max: 2 }), (req, res) => res.json({ id: 1 }))
    app.get('/endpoint2', createRateLimiter({ max: 2 }), (req, res) => res.json({ id: 2 }))

    await request(app).get('/endpoint1')
    await request(app).get('/endpoint1')

    const response1 = await request(app).get('/endpoint1')
    const response2 = await request(app).get('/endpoint2')

    expect(response1.status).toBe(429)
    expect(response2.status).toBe(200)
  })
})
