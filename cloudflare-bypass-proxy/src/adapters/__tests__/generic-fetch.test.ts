import request from 'supertest'
import express from 'express'
import { genericFetchAdapter } from '../generic-fetch'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('Generic Fetch Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(genericFetchAdapter.path, genericFetchAdapter.handler)
    jest.clearAllMocks()
  })

  it('should return 400 if url parameter is missing', async () => {
    const response = await request(app).get('/fetch')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Missing url parameter' })
  })

  it('should return 400 for invalid URL format', async () => {
    const response = await request(app).get('/fetch?url=not-a-valid-url')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Invalid URL format' })
  })

  it('should fetch HTML content by default', async () => {
    const mockHtml = '<html><body>Hello World</body></html>'

    executeCurl.mockResolvedValue({
      stdout: mockHtml,
      duration: 100,
    })

    const response = await request(app).get('/fetch?url=https://example.com')

    expect(response.status).toBe(200)
    expect(response.text).toBe(mockHtml)
    expect(response.headers['content-type']).toContain('text/html')
  })

  it('should fetch JSON content when format=json', async () => {
    const mockJson = { data: 'test', value: 123 }

    executeCurl.mockResolvedValue({
      stdout: JSON.stringify(mockJson),
      duration: 100,
    })

    const response = await request(app).get('/fetch?url=https://api.example.com/data&format=json')

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockJson)
  })

  it('should handle custom headers', async () => {
    const mockHtml = '<html><body>Authenticated</body></html>'
    const customHeaders = { Authorization: 'Bearer token123' }

    executeCurl.mockResolvedValue({
      stdout: mockHtml,
      duration: 100,
    })

    const headersParam = encodeURIComponent(JSON.stringify(customHeaders))
    const response = await request(app).get(`/fetch?url=https://example.com&headers=${headersParam}`)

    expect(response.status).toBe(200)
    expect(executeCurl).toHaveBeenCalledWith(
      expect.objectContaining({
        headers: customHeaders,
      })
    )
  })

  it('should return 400 for invalid headers JSON', async () => {
    const response = await request(app).get('/fetch?url=https://example.com&headers=invalid-json')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Invalid headers JSON format' })
  })

  it('should handle curl execution errors', async () => {
    executeCurl.mockRejectedValue(new Error('Connection refused'))

    const response = await request(app).get('/fetch?url=https://example.com')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error', 'Failed to fetch data')
  })

  it('should handle JSON parse errors when format=json', async () => {
    executeCurl.mockResolvedValue({
      stdout: 'not valid json',
      duration: 100,
    })

    const response = await request(app).get('/fetch?url=https://api.example.com&format=json')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error')
  })

  it('should have correct adapter configuration', () => {
    expect(genericFetchAdapter.path).toBe('/fetch')
    expect(genericFetchAdapter.method).toBe('GET')
    expect(genericFetchAdapter.serviceName).toBe('GenericFetch')
    expect(genericFetchAdapter.middleware).toBeDefined()
    expect(Array.isArray(genericFetchAdapter.middleware)).toBe(true)
  })
})
