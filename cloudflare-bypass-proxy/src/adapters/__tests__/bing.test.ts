import { Request, Response } from 'express'
import { bingAdapter } from '../bing'

jest.mock('child_process', () => ({
  execFile: jest.fn(),
}))

jest.mock('util', () => ({
  promisify: jest.fn(() => jest.fn()),
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

  it('should have correct adapter configuration', () => {
    expect(bingAdapter.path).toBe('/bing/images')
    expect(bingAdapter.method).toBe('POST')
    expect(bingAdapter.serviceName).toBe('BingImages')
    expect(bingAdapter.middleware).toBeDefined()
    expect(bingAdapter.middleware!.length).toBeGreaterThan(0)
  })
})
