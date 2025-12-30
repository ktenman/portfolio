import { Request, Response } from 'express'
import { duckDuckGoAdapter } from '../duckduckgo'

jest.mock('child_process', () => ({
  execFile: jest.fn(),
}))

jest.mock('util', () => ({
  promisify: jest.fn(() => jest.fn()),
}))

describe('DuckDuckGo Adapter', () => {
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
    await duckDuckGoAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should return 400 if query is empty string', async () => {
    mockReq.body = { query: '' }
    await duckDuckGoAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should return 400 if query is whitespace only', async () => {
    mockReq.body = { query: '   ' }
    await duckDuckGoAdapter.handler(mockReq as Request, mockRes as Response)
    expect(mockRes.status).toHaveBeenCalledWith(400)
    expect(mockRes.json).toHaveBeenCalledWith({
      success: false,
      results: [],
      error: 'Query is required',
    })
  })

  it('should have correct adapter configuration', () => {
    expect(duckDuckGoAdapter.path).toBe('/duckduckgo/images')
    expect(duckDuckGoAdapter.method).toBe('POST')
    expect(duckDuckGoAdapter.serviceName).toBe('DuckDuckGoImages')
    expect(duckDuckGoAdapter.middleware).toBeDefined()
    expect(duckDuckGoAdapter.middleware!.length).toBeGreaterThan(0)
  })
})
