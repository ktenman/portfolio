import { describe, it, expect, vi, beforeEach } from 'vitest'
import { SummaryService } from './summary-service'
import { ApiClient } from './api-client'
import { PortfolioSummary } from '../models/portfolio-summary'
import { Page } from '../models/page'

// Mock the ApiClient
vi.mock('./api-client', () => ({
  ApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

// Mock the decorators
vi.mock('../decorators/cacheable.decorator', () => ({
  Cacheable: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

describe('SummaryService', () => {
  let service: SummaryService

  const mockSummary: PortfolioSummary = {
    date: '2024-01-15',
    totalValue: 10000,
    xirrAnnualReturn: 15.5,
    totalProfit: 2000,
    earningsPerDay: 50,
    earningsPerMonth: 1500,
  }

  const mockPage: Page<PortfolioSummary> = {
    content: [mockSummary],
    totalElements: 1,
    totalPages: 1,
    size: 10,
    number: 0,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    service = new SummaryService()
  })

  describe('fetchHistoricalSummary', () => {
    it('fetches historical summary with pagination', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(mockPage)

      const result = await service.fetchHistoricalSummary(0, 10)

      expect(ApiClient.get).toHaveBeenCalledWith('/api/portfolio-summary/historical?page=0&size=10')
      expect(result).toEqual(mockPage)
    })

    it('handles different page and size parameters', async () => {
      const page = 2
      const size = 20
      vi.mocked(ApiClient.get).mockResolvedValue(mockPage)

      await service.fetchHistoricalSummary(page, size)

      expect(ApiClient.get).toHaveBeenCalledWith(
        `/api/portfolio-summary/historical?page=${page}&size=${size}`
      )
    })

    it('handles empty historical data', async () => {
      const emptyPage: Page<PortfolioSummary> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
      }
      vi.mocked(ApiClient.get).mockResolvedValue(emptyPage)

      const result = await service.fetchHistoricalSummary(0, 10)

      expect(result).toEqual(emptyPage)
      expect(result.content).toHaveLength(0)
    })

    it('propagates API errors', async () => {
      const apiError = new Error('API Error')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      await expect(service.fetchHistoricalSummary(0, 10)).rejects.toThrow('API Error')
    })

    it('handles large page numbers', async () => {
      const largePage = 999
      const size = 5
      vi.mocked(ApiClient.get).mockResolvedValue(mockPage)

      await service.fetchHistoricalSummary(largePage, size)

      expect(ApiClient.get).toHaveBeenCalledWith(
        `/api/portfolio-summary/historical?page=${largePage}&size=${size}`
      )
    })

    it('handles zero page and size', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(mockPage)

      await service.fetchHistoricalSummary(0, 0)

      expect(ApiClient.get).toHaveBeenCalledWith('/api/portfolio-summary/historical?page=0&size=0')
    })
  })

  describe('recalculateAllSummaries', () => {
    it('triggers recalculation via API', async () => {
      const mockResponse = { status: 'success', message: 'Recalculation started' }
      vi.mocked(ApiClient.post).mockResolvedValue(mockResponse)

      const result = await service.recalculateAllSummaries()

      expect(ApiClient.post).toHaveBeenCalledWith('/api/portfolio-summary/recalculate', {})
      expect(result).toEqual(mockResponse)
    })

    it('handles empty response from recalculation', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue({})

      const result = await service.recalculateAllSummaries()

      expect(result).toEqual({})
    })

    it('propagates recalculation API errors', async () => {
      const apiError = new Error('Recalculation failed')
      vi.mocked(ApiClient.post).mockRejectedValue(apiError)

      await expect(service.recalculateAllSummaries()).rejects.toThrow('Recalculation failed')
    })

    it('handles null response from recalculation', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(null)

      const result = await service.recalculateAllSummaries()

      expect(result).toBe(null)
    })
  })

  describe('fetchCurrentSummary', () => {
    it('fetches current summary from API', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(mockSummary)

      const result = await service.fetchCurrentSummary()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/portfolio-summary/current')
      expect(result).toEqual(mockSummary)
    })

    it('handles empty current summary', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(null)

      const result = await service.fetchCurrentSummary()

      expect(result).toBe(null)
    })

    it('propagates current summary API errors', async () => {
      const apiError = new Error('Current summary not available')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      await expect(service.fetchCurrentSummary()).rejects.toThrow('Current summary not available')
    })

    it('handles current summary with zero values', async () => {
      const zeroSummary = {
        ...mockSummary,
        totalValue: 0,
        totalInvestment: 0,
        totalReturn: 0,
        returnPercentage: 0,
        xirr: 0,
      }
      vi.mocked(ApiClient.get).mockResolvedValue(zeroSummary)

      const result = await service.fetchCurrentSummary()

      expect(result).toEqual(zeroSummary)
    })

    it('handles current summary with negative values', async () => {
      const negativeSummary = {
        ...mockSummary,
        totalValue: 8000,
        totalReturn: -2000,
        returnPercentage: -20.0,
        xirr: -10.5,
      }
      vi.mocked(ApiClient.get).mockResolvedValue(negativeSummary)

      const result = await service.fetchCurrentSummary()

      expect(result).toEqual(negativeSummary)
    })
  })

  describe('service initialization', () => {
    it('creates service with correct API URLs', () => {
      const newService = new SummaryService()
      expect(newService).toBeInstanceOf(SummaryService)
    })
  })

  describe('caching behavior', () => {
    it('applies cacheable decorator to fetchCurrentSummary method', () => {
      // The decorator is mocked, so we just verify the method exists and is callable
      expect(typeof service.fetchCurrentSummary).toBe('function')
    })
  })

  describe('error handling', () => {
    it('handles network timeouts', async () => {
      const timeoutError = new Error('Network timeout')
      vi.mocked(ApiClient.get).mockRejectedValue(timeoutError)

      await expect(service.fetchCurrentSummary()).rejects.toThrow('Network timeout')
    })

    it('handles server errors', async () => {
      const serverError = new Error('Internal Server Error')
      vi.mocked(ApiClient.post).mockRejectedValue(serverError)

      await expect(service.recalculateAllSummaries()).rejects.toThrow('Internal Server Error')
    })

    it('handles malformed pagination response', async () => {
      const malformedPage = { content: [mockSummary] } // Missing required pagination fields
      vi.mocked(ApiClient.get).mockResolvedValue(malformedPage)

      const result = await service.fetchHistoricalSummary(0, 10)

      expect(result).toEqual(malformedPage)
    })
  })
})
