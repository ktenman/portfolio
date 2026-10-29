import { Request, Response } from 'express'
import { bingAdapter } from '../bing'

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

describe('Bing Adapter', () => {
  let mockReq: Partial<Request>
  let mockRes: Partial<Response>

  beforeEach(() => {
    mockReq = { body: {} }
    mockRes = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    }
    jest.clearAllMocks()
  })

  it('should return 400 if query parameter is missing', async () => {
    mockReq.body = {}
    await bingAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should return 400 if query is empty string', async () => {
    mockReq.body = { query: '' }
    await bingAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should return 400 if query is whitespace only', async () => {
    mockReq.body = { query: '   ' }
    await bingAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should leave the User-Agent to the impersonated browser', async () => {
    jest.spyOn(console, 'log').mockImplementation()
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) =>
      callback(null, { stdout: '', stderr: '' })
    )
    mockReq.body = { query: 'Ühispank' }

    await bingAdapter.handler(mockReq as Request, mockRes as Response)

    expect(mockExecFile.mock.calls[0][1]).not.toContainEqual(expect.stringMatching(/^user-agent:/i))
  })

  it('should have correct adapter configuration', () => {
    expect(bingAdapter.path).toBe('/bing/images')
    expect(bingAdapter.method).toBe('POST')
    expect(bingAdapter.serviceName).toBe('BingImages')
    expect(bingAdapter.middleware).toBeDefined()
    expect(bingAdapter.middleware!.length).toBeGreaterThan(0)
  })
})
