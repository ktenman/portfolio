import { describe, it, expect, vi, beforeEach } from 'vitest'
import { portfolioSummaryService } from './portfolio-summary-service'
import { httpClient } from '../utils/http-client'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe('portfolioSummaryService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const mockPortfolioSummary: PortfolioSummaryDto = {
    date: '2023-12-31',
    totalValue: 50000,
    realizedProfit: 0,
    unrealizedProfit: 5000,
    totalProfit: 5000,
    xirrAnnualReturn: 0.12,
    earningsPerDay: 100,
    earningsPerMonth: 3000,
    totalProfitChange24h: null,
  }

  describe('getHistorical', () => {
    it('should fetch historical data with pagination', async () => {
      const mockPage: Page<PortfolioSummaryDto> = {
        content: [
          mockPortfolioSummary,
          { ...mockPortfolioSummary, date: '2023-12-30', totalValue: 49750 },
        ],
        totalElements: 100,
        totalPages: 10,
        size: 10,
        number: 0,
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockPage,
      })

      const result = await portfolioSummaryService.getHistorical(0, 10)

      expect(httpClient.get).toHaveBeenCalledWith('/portfolio-summary/historical', {
        params: { page: 0, size: 10 },
      })
      expect(result).toEqual(mockPage)
    })

    it('should handle different page and size parameters', async () => {
      const mockPage: Page<PortfolioSummaryDto> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 5,
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockPage,
      } as any)

      const result = await portfolioSummaryService.getHistorical(5, 20)

      expect(httpClient.get).toHaveBeenCalledWith('/portfolio-summary/historical', {
        params: { page: 5, size: 20 },
      })
      expect(result).toEqual(mockPage)
    })

    it('should propagate errors on getHistorical', async () => {
      const error = new Error('Failed to fetch historical data')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(portfolioSummaryService.getHistorical(0, 10)).rejects.toThrow(
        'Failed to fetch historical data'
      )
    })
  })

  describe('getCurrent', () => {
    it('should fetch current portfolio summary', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockPortfolioSummary,
      } as any)

      const result = await portfolioSummaryService.getCurrent()

      expect(httpClient.get).toHaveBeenCalledWith('/portfolio-summary/current')
      expect(result).toEqual(mockPortfolioSummary)
    })

    it('should propagate errors on getCurrent', async () => {
      const error = new Error('No portfolio data available')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(portfolioSummaryService.getCurrent()).rejects.toThrow(
        'No portfolio data available'
      )
    })
  })

  describe('recalculate', () => {
    it('should trigger portfolio recalculation', async () => {
      const mockResponse = { message: 'Portfolio recalculation started' }

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: mockResponse,
      } as any)

      const result = await portfolioSummaryService.recalculate()

      expect(httpClient.post).toHaveBeenCalledWith('/portfolio-summary/recalculate', undefined, {
        timeout: 60000,
      })
      expect(result).toEqual(mockResponse)
    })

    it('should call recalculate without request body', async () => {
      const mockResponse = { message: 'Recalculation in progress' }

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: mockResponse,
      } as any)

      await portfolioSummaryService.recalculate()

      expect(httpClient.post).toHaveBeenCalledWith('/portfolio-summary/recalculate', undefined, {
        timeout: 60000,
      })
      expect(vi.mocked(httpClient.post).mock.calls[0].length).toBe(3)
    })

    it('should propagate errors on recalculate', async () => {
      const error = new Error('Recalculation service unavailable')
      vi.mocked(httpClient.post).mockRejectedValueOnce(error)

      await expect(portfolioSummaryService.recalculate()).rejects.toThrow(
        'Recalculation service unavailable'
      )
    })
  })
})
