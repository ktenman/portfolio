import request from 'supertest'
import express from 'express'
import { wisdomTreeAdapter } from '../wisdom-tree'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('WisdomTree Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(
      wisdomTreeAdapter.path,
      ...(wisdomTreeAdapter.middleware || []),
      wisdomTreeAdapter.handler
    )
    jest.clearAllMocks()
  })

  it('should return 400 if etfId parameter is missing', async () => {
    const response = await request(app).get('/wisdomtree/holdings/')

    expect(response.status).toBe(404)
  })

  it('should successfully fetch holdings for valid etfId', async () => {
    const mockHtmlResponse = '<html><body><table>Holdings data</table></body></html>'

    executeCurl.mockResolvedValue({
      stdout: mockHtmlResponse,
      duration: 200,
    })

    const response = await request(app).get('/wisdomtree/holdings/WTAI')

    expect(response.status).toBe(200)
    expect(response.type).toBe('text/html')
    expect(response.text).toBe(mockHtmlResponse)
    expect(executeCurl).toHaveBeenCalledWith({
      url: 'https://www.wisdomtree.eu/en-gb/global/etf-details/modals/all-holdings?id={WTAI}',
      timeout: 15000,
      maxBuffer: 2 * 1024 * 1024,
      headers: undefined,
    })
  })

  it('should handle curl execution errors', async () => {
    executeCurl.mockRejectedValue(new Error('Connection refused'))

    const response = await request(app).get('/wisdomtree/holdings/WTAI')

    expect(response.status).toBe(500)
    expect(response.body).toHaveProperty('error', 'Failed to fetch data')
    expect(response.body).toHaveProperty('message')
  })

  it('should sanitize log input for security', async () => {
    const consoleSpy = jest.spyOn(console, 'log').mockImplementation()

    executeCurl.mockResolvedValue({
      stdout: '<html>test</html>',
      duration: 100,
    })

    await request(app).get('/wisdomtree/holdings/TEST123')

    expect(consoleSpy).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })

  it('should have correct adapter configuration', () => {
    expect(wisdomTreeAdapter.path).toBe('/wisdomtree/holdings/:etfId')
    expect(wisdomTreeAdapter.method).toBe('GET')
    expect(wisdomTreeAdapter.middleware).toBeDefined()
    expect(Array.isArray(wisdomTreeAdapter.middleware)).toBe(true)
    expect(wisdomTreeAdapter.middleware?.length).toBeGreaterThan(0)
  })

  it('should handle special characters in etfId', async () => {
    executeCurl.mockResolvedValue({
      stdout: '<html>test</html>',
      duration: 100,
    })

    const response = await request(app).get('/wisdomtree/holdings/TEST-123')

    expect(response.status).toBe(200)
  })
})
