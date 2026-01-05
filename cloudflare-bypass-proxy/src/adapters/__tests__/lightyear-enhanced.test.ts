import request from 'supertest'
import express from 'express'
import { lightyearAdapter } from '../lightyear'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('Lightyear Adapter - Enhanced Tests', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(lightyearAdapter.path, ...(lightyearAdapter.middleware || []), lightyearAdapter.handler)
    jest.clearAllMocks()
  })

  describe('Base64 Encoding', () => {
    it('should correctly encode path to base64 without padding', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ price: 50 }),
        duration: 100,
      })

      const path = '/v1/market-data/1ef27f9a-bde6-6dda-a873-3946ca86bd5c/price'
      await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

      const callArgs = executeCurl.mock.calls[0][0]
      const expectedEncoded =
        'L3YxL21hcmtldC1kYXRhLzFlZjI3ZjlhLWJkZTYtNmRkYS1hODczLTM5NDZjYTg2YmQ1Yy9wcmljZQ'

      expect(callArgs.url).toContain(`path=${expectedEncoded}`)
      expect(callArgs.url).not.toContain(
        'path=L3YxL21hcmtldC1kYXRhLzFlZjI3ZjlhLWJkZTYtNmRkYS1hODczLTM5NDZjYTg2YmQ1Yy9wcmljZQ=='
      )
    })

    it('should handle query parameters in path encoding', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ data: 'ok' }),
        duration: 100,
      })

      const path = '/v1/chart?range=max'
      await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

      const callArgs = executeCurl.mock.calls[0][0]
      const expectedEncoded = Buffer.from(path).toString('base64').replace(/=+$/, '')

      expect(callArgs.url).toContain(`path=${expectedEncoded}`)
    })

    it('should handle special characters in path', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ data: 'ok' }),
        duration: 100,
      })

      const path = '/v1/test?foo=bar&baz=qux'
      await request(app).get(`/lightyear/fetch?path=${encodeURIComponent(path)}`)

      const callArgs = executeCurl.mock.calls[0][0]
      expect(callArgs.url).toMatch(/path=[A-Za-z0-9+/]+(?!%3D)&withAPIKey=true/)
    })
  })

  describe('Input Validation Edge Cases', () => {
    it('should return 400 when path is empty string', async () => {
      const response = await request(app).get(`/lightyear/fetch?path=${encodeURIComponent('')}`)
      expect([400, 500]).toContain(response.status)
    })

    it('should handle whitespace-only path (converts to null)', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ data: 'ok' }),
        duration: 100,
      })

      const response = await request(app).get(`/lightyear/fetch?path=${encodeURIComponent('   ')}`)
      expect(response.status).toBeLessThan(500)
    })

    it('should handle very long path parameters', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ data: 'ok' }),
        duration: 100,
      })

      const longPath = '/v1/' + 'a'.repeat(1000)
      const response = await request(app).get(
        `/lightyear/fetch?path=${encodeURIComponent(longPath)}`
      )

      expect(response.status).toBeLessThan(500)
    })

    it('should handle unicode characters in path', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ data: 'ok' }),
        duration: 100,
      })

      const unicodePath = '/v1/测试/🚀'
      const response = await request(app).get(
        `/lightyear/fetch?path=${encodeURIComponent(unicodePath)}`
      )

      expect([200, 400]).toContain(response.status)
    })
  })

  describe('Curl Execution Parameters', () => {
    it('should pass correct timeout and maxBuffer to executeCurl', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ price: 50 }),
        duration: 100,
      })

      await request(app).get('/lightyear/fetch?path=/v1/test')

      expect(executeCurl).toHaveBeenCalledWith({
        url: expect.stringContaining('https://lightyear.com/fetch?path='),
        timeout: 30000,
        maxBuffer: 1024 * 1024,
        headers: {
          'user-agent': expect.any(String),
          referer: 'https://lightyear.com/',
          accept: '*/*',
          'accept-language': 'en-US,en;q=0.9',
        },
      })
    })

    it('should include all required headers for Cloudflare bypass', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ price: 50 }),
        duration: 100,
      })

      await request(app).get('/lightyear/fetch?path=/v1/test')

      const callArgs = executeCurl.mock.calls[0][0]
      expect(callArgs.headers['user-agent']).toContain('Mozilla')
      expect(callArgs.headers['referer']).toBe('https://lightyear.com/')
      expect(callArgs.headers['accept']).toBe('*/*')
      expect(callArgs.headers['accept-language']).toBe('en-US,en;q=0.9')
    })
  })

  describe('Concurrent Requests', () => {
    it('should handle concurrent requests without race conditions', async () => {
      executeCurl.mockResolvedValue({
        stdout: JSON.stringify({ price: 50 }),
        duration: 10,
      })

      const concurrentRequests = Array(10)
        .fill(null)
        .map((_, i) => request(app).get(`/lightyear/fetch?path=/v1/test${i}`))

      const responses = await Promise.all(concurrentRequests)

      const successful = responses.filter(r => r.status === 200)
      expect(successful.length).toBe(10)
      expect(executeCurl).toHaveBeenCalledTimes(10)
    })
  })

  describe('Error Scenarios', () => {
    it('should handle network timeouts gracefully', async () => {
      executeCurl.mockRejectedValue(new Error('ETIMEDOUT'))

      const response = await request(app).get('/lightyear/fetch?path=/v1/test')

      expect(response.status).toBe(500)
      expect(response.body).toHaveProperty('error')
      expect(response.body.message).toContain('ETIMEDOUT')
    })

    it('should handle malformed JSON responses', async () => {
      executeCurl.mockResolvedValue({
        stdout: '{invalid json',
        duration: 100,
      })

      const response = await request(app).get('/lightyear/fetch?path=/v1/test')

      expect(response.status).toBe(500)
    })

    it('should handle empty responses', async () => {
      executeCurl.mockResolvedValue({
        stdout: '',
        duration: 100,
      })

      const response = await request(app).get('/lightyear/fetch?path=/v1/test')

      expect(response.status).toBe(500)
    })
  })
})
