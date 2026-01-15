import { describe, it, expect, vi, beforeEach } from 'vitest'
import { diversificationService } from './diversification-service'
import { httpClient } from '../utils/http-client'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe('diversificationService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getAvailableEtfs', () => {
    it('should fetch available etfs', async () => {
      const mockEtfs = [
        {
          instrumentId: 1,
          symbol: 'VWCE',
          name: 'Vanguard FTSE All-World',
          allocation: 0,
          ter: 0.22,
          annualReturn: 0.12,
          currentPrice: 120.5,
        },
        {
          instrumentId: 2,
          symbol: 'VUAA',
          name: 'Vanguard S&P 500',
          allocation: 0,
          ter: 0.07,
          annualReturn: 0.15,
          currentPrice: 95.3,
        },
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce({ data: mockEtfs })

      const result = await diversificationService.getAvailableEtfs()

      expect(httpClient.get).toHaveBeenCalledWith('/diversification/available-etfs')
      expect(result).toEqual(mockEtfs)
    })

    it('should handle empty response', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce({ data: [] })

      const result = await diversificationService.getAvailableEtfs()

      expect(httpClient.get).toHaveBeenCalledWith('/diversification/available-etfs')
      expect(result).toEqual([])
    })

    it('should propagate errors', async () => {
      const error = new Error('Network error')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(diversificationService.getAvailableEtfs()).rejects.toThrow('Network error')
    })
  })

  describe('calculate', () => {
    it('should calculate diversification with single allocation', async () => {
      const allocations = [{ instrumentId: 1, percentage: 100 }]
      const mockResponse = {
        weightedTer: 0.22,
        weightedAnnualReturn: 0.12,
        totalUniqueHoldings: 3500,
        etfDetails: [
          {
            instrumentId: 1,
            symbol: 'VWCE',
            name: 'Vanguard FTSE All-World',
            allocation: 100,
            ter: 0.22,
            annualReturn: 0.12,
            currentPrice: 120.5,
          },
        ],
        holdings: [
          { name: 'Apple Inc', ticker: 'AAPL', percentage: 5.5, inEtfs: 'VWCE' },
          { name: 'Microsoft Corp', ticker: 'MSFT', percentage: 4.8, inEtfs: 'VWCE' },
        ],
        sectors: [
          { sector: 'Technology', percentage: 25.5 },
          { sector: 'Financials', percentage: 15.2 },
        ],
        countries: [
          { countryCode: 'US', countryName: 'United States', percentage: 60.5 },
          { countryCode: 'JP', countryName: 'Japan', percentage: 6.2 },
        ],
        concentration: {
          top10Percentage: 18.5,
          largestPosition: { name: 'Apple Inc', percentage: 5.5 },
        },
      }
      vi.mocked(httpClient.post).mockResolvedValueOnce({ data: mockResponse })

      const result = await diversificationService.calculate(allocations)

      expect(httpClient.post).toHaveBeenCalledWith('/diversification/calculate', { allocations })
      expect(result).toEqual(mockResponse)
    })

    it('should calculate diversification with multiple allocations', async () => {
      const allocations = [
        { instrumentId: 1, percentage: 60 },
        { instrumentId: 2, percentage: 40 },
      ]
      const mockResponse = {
        weightedTer: 0.16,
        weightedAnnualReturn: 0.132,
        totalUniqueHoldings: 4000,
        etfDetails: [
          {
            instrumentId: 1,
            symbol: 'VWCE',
            name: 'Vanguard FTSE All-World',
            allocation: 60,
            ter: 0.22,
            annualReturn: 0.12,
            currentPrice: 120.5,
          },
          {
            instrumentId: 2,
            symbol: 'VUAA',
            name: 'Vanguard S&P 500',
            allocation: 40,
            ter: 0.07,
            annualReturn: 0.15,
            currentPrice: 95.3,
          },
        ],
        holdings: [],
        sectors: [],
        countries: [],
        concentration: {
          top10Percentage: 20.0,
          largestPosition: null,
        },
      }
      vi.mocked(httpClient.post).mockResolvedValueOnce({ data: mockResponse })

      const result = await diversificationService.calculate(allocations)

      expect(httpClient.post).toHaveBeenCalledWith('/diversification/calculate', { allocations })
      expect(result.weightedTer).toBe(0.16)
      expect(result.etfDetails).toHaveLength(2)
    })

    it('should handle empty allocations', async () => {
      const allocations: { instrumentId: number; percentage: number }[] = []
      const mockResponse = {
        weightedTer: 0,
        weightedAnnualReturn: 0,
        totalUniqueHoldings: 0,
        etfDetails: [],
        holdings: [],
        sectors: [],
        countries: [],
        concentration: {
          top10Percentage: 0,
          largestPosition: null,
        },
      }
      vi.mocked(httpClient.post).mockResolvedValueOnce({ data: mockResponse })

      const result = await diversificationService.calculate(allocations)

      expect(httpClient.post).toHaveBeenCalledWith('/diversification/calculate', { allocations })
      expect(result.totalUniqueHoldings).toBe(0)
    })

    it('should propagate errors on calculate', async () => {
      const error = new Error('Calculation failed')
      vi.mocked(httpClient.post).mockRejectedValueOnce(error)

      await expect(
        diversificationService.calculate([{ instrumentId: 1, percentage: 100 }])
      ).rejects.toThrow('Calculation failed')
    })

    it('should handle server validation errors', async () => {
      const error = new Error('Invalid allocation percentages')
      vi.mocked(httpClient.post).mockRejectedValueOnce(error)

      await expect(
        diversificationService.calculate([{ instrumentId: -1, percentage: 150 }])
      ).rejects.toThrow('Invalid allocation percentages')
    })
  })
})
