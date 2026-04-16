import request from 'supertest'
import express from 'express'
import { trading212EtfHoldingsAdapter, trading212EtfSummaryAdapter } from '../trading212-etf'

jest.mock('../../utils/curl-executor', () => ({
  executeCurl: jest.fn(),
}))

const { executeCurl } = require('../../utils/curl-executor')

describe('Trading212 ETF Adapters', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.get(trading212EtfHoldingsAdapter.path, trading212EtfHoldingsAdapter.handler)
    app.get(trading212EtfSummaryAdapter.path, trading212EtfSummaryAdapter.handler)
    jest.clearAllMocks()
  })

  it('should return 400 when holdings endpoint is called without ticker', async () => {
    const response = await request(app).get('/trading212/etf-holdings')
    expect(response.status).toBe(400)
  })

  it('should proxy holdings request to upstream with ticker', async () => {
    const mockResponse = [{ ticker: 'SANe_EQ', percentage: 13.94, externalName: null }]
    executeCurl.mockResolvedValue({ stdout: JSON.stringify(mockResponse), duration: 100 })

    const response = await request(app).get('/trading212/etf-holdings?ticker=BNKEp_EQ')

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockResponse)
    expect(executeCurl).toHaveBeenCalledWith(
      expect.objectContaining({
        url: 'https://live.services.trading212.com/rest/v2/exchange-traded-funds/holdings?ticker=BNKEp_EQ',
      })
    )
  })

  it('should proxy summary request to upstream with ticker', async () => {
    const mockResponse = { expenseRatio: 0.3, holdingsCount: 29, holdings: [] }
    executeCurl.mockResolvedValue({ stdout: JSON.stringify(mockResponse), duration: 100 })

    const response = await request(app).get('/trading212/etf-summary?ticker=BNKEp_EQ')

    expect(response.status).toBe(200)
    expect(response.body).toEqual(mockResponse)
    expect(executeCurl).toHaveBeenCalledWith(
      expect.objectContaining({
        url: 'https://live.services.trading212.com/rest/v2/exchange-traded-funds?ticker=BNKEp_EQ',
      })
    )
  })

  it('should return 500 when curl fails', async () => {
    executeCurl.mockRejectedValue(new Error('Network timeout'))

    const response = await request(app).get('/trading212/etf-holdings?ticker=BNKEp_EQ')

    expect(response.status).toBe(500)
  })
})
