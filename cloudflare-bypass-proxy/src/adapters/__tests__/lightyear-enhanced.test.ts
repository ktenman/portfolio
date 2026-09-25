import request from 'supertest'
import express from 'express'
import { lightyearBatchAdapter, lightyearLookupAdapter } from '../lightyear'

const mockExecFile = jest.fn()

jest.mock('child_process', () => ({
  execFile: (...args: unknown[]) => mockExecFile(...args),
}))

jest.mock('util', () => ({
  ...jest.requireActual('util'),
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

describe('Lightyear lookup and batch routes', () => {
  let app: express.Application
  let logSpy: jest.SpyInstance
  let errorSpy: jest.SpyInstance

  beforeEach(() => {
    jest.resetAllMocks()
    logSpy = jest.spyOn(console, 'log').mockImplementation()
    errorSpy = jest.spyOn(console, 'error').mockImplementation()
    jest.spyOn(console, 'warn').mockImplementation()
    app = express()
    app.use(express.json())
    app.get(
      lightyearLookupAdapter.path,
      ...(lightyearLookupAdapter.middleware || []),
      lightyearLookupAdapter.handler
    )
    app.post(
      lightyearBatchAdapter.path,
      ...(lightyearBatchAdapter.middleware || []),
      lightyearBatchAdapter.handler
    )
  })

  afterEach(() => {
    jest.restoreAllMocks()
  })

  it('should look up the exact ticker and exchange through the public search API', async () => {
    respond(
      JSON.stringify({
        results: [
          { instrument: { id: 'partial', symbol: 'VUAA1', exchange: 'LSE', currency: 'USD' } },
          { instrument: { id: 'germany', symbol: 'VUAA', exchange: 'XETRA', currency: 'EUR' } },
          { instrument: { id: 'london', symbol: 'VUAA', exchange: 'LSE', currency: 'USD' } },
        ],
      })
    )

    const response = await request(app).get('/lightyear/lookup').query({ symbol: 'VUAA:LON:USD' })

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ symbol: 'VUAA:LON:USD', uuid: 'london' })
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        'https://lightyear.com/site-api/public/v1/instrument/search?value=VUAA',
      ]),
      { timeout: 10000, maxBuffer: 1024 * 1024 },
      expect.any(Function)
    )
  })

  it('should encode special characters in the lookup ticker', async () => {
    respond(
      '{"results":[{"instrument":{"id":"matched","symbol":"A&B","exchange":"LSE","currency":"USD"}}]}'
    )

    const response = await request(app).get('/lightyear/lookup').query({ symbol: 'A&B:LON:USD' })

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ symbol: 'A&B:LON:USD', uuid: 'matched' })
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        'https://lightyear.com/site-api/public/v1/instrument/search?value=A%26B',
      ]),
      expect.any(Object),
      expect.any(Function)
    )
  })

  it('should send batch POST requests to the existing API endpoint', async () => {
    respond('[{"id":"instrument-id","name":"Example fund"}]')

    const response = await request(app).post('/lightyear/batch').send(['instrument-id'])

    expect(response.status).toBe(200)
    expect(response.body).toEqual([{ id: 'instrument-id', name: 'Example fund' }])
    expect(mockExecFile).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        'https://api.lightyear.com/v1/instrument/batch',
        '-X',
        'POST',
        '-d',
        '["instrument-id"]',
        '-H',
        'Content-Type: application/json',
      ]),
      { timeout: 10000, maxBuffer: 5 * 1024 * 1024 },
      expect.any(Function)
    )
  })

  it('should reject an empty batch before contacting Lightyear', async () => {
    const response = await request(app).post('/lightyear/batch').send([])

    expect(response.status).toBe(400)
    expect(mockExecFile).not.toHaveBeenCalled()
  })

  it('should reject a missing lookup symbol before contacting Lightyear', async () => {
    const response = await request(app).get('/lightyear/lookup')

    expect(response.status).toBe(400)
    expect(mockExecFile).not.toHaveBeenCalled()
  })

  it('should preserve instrument-not-found behavior for a valid empty search', async () => {
    respond('{"results":[]}')

    const response = await request(app).get('/lightyear/lookup').query({ symbol: 'MISSING' })

    expect(response.status).toBe(404)
    expect(response.body).toEqual({ error: 'Instrument not found', symbol: 'MISSING' })
  })

  describe.each(['lookup', 'batch'])('%s upstream errors', route => {
    function sendRequest(): request.Test {
      if (route === 'lookup')
        return request(app).get('/lightyear/lookup').query({ symbol: 'VUAA:LON:USD' })
      return request(app).post('/lightyear/batch').send(['instrument-id'])
    }

    it.each([
      [404, 'text/html', '<html>sensitive-upstream-token</html>'],
      [401, 'application/json', '{"error":"sensitive-upstream-token"}'],
      [429, 'application/json', '{"error":"sensitive-upstream-token"}'],
      [502, 'text/html', '<html>sensitive-upstream-token</html>'],
    ])(
      'should preserve upstream status %i and classify it as an HTTP failure',
      async (status, contentType, body) => {
        respond(body, status, contentType)

        const response = await sendRequest()

        expect(response.status).toBe(status)
        expect(response.body).toEqual({
          error: 'Lightyear upstream request failed',
          code: 'UPSTREAM_HTTP_ERROR',
          upstreamStatus: status,
          contentType,
        })
        const logs = [...logSpy.mock.calls, ...errorSpy.mock.calls].flat().join(' ')
        expect(logs).toContain('UPSTREAM_HTTP_ERROR')
        expect(logs).not.toMatch(/sensitive-upstream-token|completed/i)
        expect(response.text).not.toContain('sensitive-upstream-token')
      }
    )

    it.each([
      [200, 'application/json', '{sensitive-upstream-token', 'UPSTREAM_INVALID_JSON'],
      [200, 'application/json', 'null', 'UPSTREAM_INVALID_PAYLOAD'],
      [
        200,
        'text/html',
        '<html>sensitive-upstream-token</html>',
        'UPSTREAM_UNEXPECTED_CONTENT_TYPE',
      ],
      [204, '', '', 'UPSTREAM_EMPTY_RESPONSE'],
    ])('should reject invalid upstream data %i %s %s', async (status, contentType, body, code) => {
      respond(body, status, contentType)

      const response = await sendRequest()

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
      [{ code: 6 }, 502, 'UPSTREAM_NETWORK_ERROR'],
    ])('should classify transport failure %j', async (fields, status, code) => {
      const failure = Object.assign(
        new Error('curl -H Authorization: sensitive-upstream-token'),
        fields
      )
      mockExecFile.mockImplementation((_cmd, _args, _opts, callback) => callback(failure, null))

      const response = await sendRequest()

      expect(response.status).toBe(status)
      expect(response.body).toEqual({ error: 'Lightyear upstream request failed', code })
      const logs = [...logSpy.mock.calls, ...errorSpy.mock.calls].flat().join(' ')
      expect(logs).toContain(code)
      expect(logs).not.toMatch(/sensitive-upstream-token|Authorization|completed/i)
      expect(response.text).not.toMatch(/sensitive-upstream-token|Authorization/)
    })
  })
})
