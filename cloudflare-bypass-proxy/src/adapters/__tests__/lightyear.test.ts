import type { MockInstance } from 'vitest'
import request from 'supertest'
import express from 'express'
import { lightyearAdapter } from '../lightyear'

const mockExecFile = vi.fn()

vi.mock('child_process', () => ({
  execFile: (...args: unknown[]) => mockExecFile(...args),
}))

vi.mock('util', async () => ({
  ...(await vi.importActual('util')),
  promisify:
    (fn: (...args: unknown[]) => void) =>
    (...args: unknown[]) =>
      new Promise((resolve, reject) => {
        fn(...args, (error: Error | null, result: unknown) => {
          if (error) reject(error)
          else resolve(result)
        })
      }),
}))

function respond(body: string, status = 200, contentType = 'application/json'): void {
  mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => {
    callback(null, { stdout: `${body}\n${status}\n${contentType}`, stderr: '' })
  })
}

describe('Lightyear fetch route', () => {
  let app: express.Application
  let logSpy: MockInstance
  let errorSpy: MockInstance

  beforeEach(() => {
    vi.resetAllMocks()
    logSpy = vi.spyOn(console, 'log').mockImplementation(() => undefined)
    errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    app = express()
    app.get(lightyearAdapter.path, ...(lightyearAdapter.middleware || []), lightyearAdapter.handler)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should reject a missing path without contacting Lightyear', async () => {
    const response = await request(app).get('/lightyear/fetch')

    expect(response.status).toBe(400)
    expect(response.body).toEqual({ error: 'Missing path parameter' })
    expect(mockExecFile).not.toHaveBeenCalled()
  })

  it.each([
    [
      '/v1/market-data/instrument-id/price',
      'https://lightyear.com/site-api/public/v1/market-data/instrument-id/price',
      { price: '42.50', currency: 'EUR' },
    ],
    [
      'v1/instrument/instrument-id/fund-info',
      'https://lightyear.com/site-api/public/v1/instrument/instrument-id/fund-info',
      { name: 'Example fund' },
    ],
    [
      '/v1/instrument/instrument-id/holdings',
      'https://lightyear.com/site-api/public/v1/instrument/instrument-id/holdings',
      { holdings: [{ name: 'Example', weight: '0.25' }] },
    ],
    [
      '/v1/market-data/instrument-id/chart?range=5y',
      'https://lightyear.com/site-api/public/v1/market-data/instrument-id/chart?range=5y',
      { data: [{ date: '2025-01-02', value: '123.45' }] },
    ],
    [
      '/v1/market-data/instrument-id/chart?range=max&currency=EUR',
      'https://lightyear.com/site-api/public/v1/market-data/instrument-id/chart?range=max&currency=EUR',
      { data: [{ date: '2025-01-02', value: '123.45' }] },
    ],
  ])('should fetch %s through the public site API', async (path, upstreamUrl, payload) => {
    respond(JSON.stringify(payload), 200, 'application/json; charset=utf-8')

    const response = await request(app).get('/lightyear/fetch').query({ path })

    expect(response.status).toBe(200)
    expect(response.body).toEqual(payload)
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([upstreamUrl, '-H', 'referer: https://lightyear.com/']),
      { timeout: 10000, maxBuffer: 1024 * 1024 },
      expect.any(Function)
    )
    expect(logSpy.mock.calls.flat().join(' ')).toMatch(/completed/i)
  })

  it('should leave the User-Agent to the impersonated browser', async () => {
    respond('{"price":"42.50"}')

    await request(app).get('/lightyear/fetch').query({ path: '/v1/market-data/id/price' })

    expect(mockExecFile.mock.calls[0][1]).not.toContainEqual(expect.stringMatching(/^user-agent:/i))
  })

  it.each([
    [404, 'text/html; charset=utf-8', '<!DOCTYPE html><html>sensitive-upstream-token</html>'],
    [401, 'application/json', '{"error":"sensitive-upstream-token"}'],
    [429, 'application/json', '{"error":"sensitive-upstream-token"}'],
    [502, 'text/html', '<html>sensitive-upstream-token</html>'],
  ])(
    'should preserve upstream HTTP %i without leaking its body',
    async (status, contentType, body) => {
      respond(body, status, contentType)

      const response = await request(app)
        .get('/lightyear/fetch')
        .query({ path: '/v1/test?access_token=request-secret' })

      expect(response.status).toBe(status)
      expect(response.body).toEqual({
        error: 'Lightyear upstream request failed',
        code: 'UPSTREAM_HTTP_ERROR',
        upstreamStatus: status,
        contentType,
      })
      expect(response.headers['content-type']).toContain('application/json')
      const logs = [...logSpy.mock.calls, ...errorSpy.mock.calls].flat().join(' ')
      expect(logs).toContain('UPSTREAM_HTTP_ERROR')
      expect(logs).toContain(String(status))
      expect(logs).not.toMatch(/sensitive-upstream-token|request-secret|completed/i)
      expect(response.text).not.toMatch(/sensitive-upstream-token|request-secret/)
    }
  )

  it.each([
    [200, 'text/html', '<html>sensitive-upstream-token</html>', 'UPSTREAM_UNEXPECTED_CONTENT_TYPE'],
    [200, 'text/plain', '{"price":42}', 'UPSTREAM_UNEXPECTED_CONTENT_TYPE'],
    [200, '', '{"price":42}', 'UPSTREAM_UNEXPECTED_CONTENT_TYPE'],
    [200, 'application/json', '{sensitive-upstream-token', 'UPSTREAM_INVALID_JSON'],
    [200, 'application/json', '', 'UPSTREAM_EMPTY_RESPONSE'],
    [204, '', '', 'UPSTREAM_EMPTY_RESPONSE'],
  ])('should reject invalid upstream JSON: %i %s %s', async (status, contentType, body, code) => {
    respond(body, status, contentType)

    const response = await request(app).get('/lightyear/fetch').query({ path: '/v1/test' })

    expect(response.status).toBe(502)
    expect(response.body).toEqual({
      error: 'Lightyear returned an invalid response',
      code,
      upstreamStatus: status,
      contentType,
    })
    const logs = [...logSpy.mock.calls, ...errorSpy.mock.calls].flat().join(' ')
    expect(logs).toContain(code)
    expect(logs).not.toMatch(/sensitive-upstream-token|completed/i)
    expect(response.text).not.toContain('sensitive-upstream-token')
  })

  it.each([
    [{ code: 28 }, 504, 'UPSTREAM_TIMEOUT'],
    [{ code: null, killed: true, signal: 'SIGTERM' }, 504, 'UPSTREAM_TIMEOUT'],
    [{ code: 6 }, 502, 'UPSTREAM_NETWORK_ERROR'],
  ])('should classify transport failures %j', async (fields, status, code) => {
    const failure = Object.assign(
      new Error('curl -H Authorization: sensitive-upstream-token'),
      fields
    )
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => callback(failure, null))

    const response = await request(app).get('/lightyear/fetch').query({ path: '/v1/test' })

    expect(response.status).toBe(status)
    expect(response.body).toEqual({ error: 'Lightyear upstream request failed', code })
    const logs = [...logSpy.mock.calls, ...errorSpy.mock.calls].flat().join(' ')
    expect(logs).toContain(code)
    expect(logs).not.toMatch(/sensitive-upstream-token|Authorization|completed/i)
    expect(response.text).not.toMatch(/sensitive-upstream-token|Authorization/)
  })

  it('should accept structured JSON media types', async () => {
    respond('{"price":42}', 200, 'application/vnd.lightyear+json; charset=utf-8')

    const response = await request(app).get('/lightyear/fetch').query({ path: '/v1/test' })

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ price: 42 })
  })
})
