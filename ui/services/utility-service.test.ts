import { describe, it, expect, vi, beforeEach } from 'vitest'
import { utilityService } from './utility-service'
import { httpClient } from '../utils/http-client'
import type { CalculationResult } from '../models/calculation-result'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
  },
}))

describe('utilityService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getCalculationResult', () => {
    it('should fetch calculation result', async () => {
      const mockCalculationResult: CalculationResult = {
        xirrs: [
          { date: '2023-01-01', amount: 1000 },
          { date: '2023-02-01', amount: 2000 },
          { date: '2023-03-01', amount: 3000 },
        ],
        median: 11.5,
        average: 12.5,
        total: 50000,
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockCalculationResult,
      } as any)

      const result = await utilityService.getCalculationResult()

      expect(httpClient.get).toHaveBeenCalledWith('/calculator')
      expect(result).toEqual(mockCalculationResult)
    })

    it('should handle empty arrays in calculation result', async () => {
      const mockEmptyResult: CalculationResult = {
        xirrs: [],
        median: 0,
        average: 0,
        total: 0,
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockEmptyResult,
      } as any)

      const result = await utilityService.getCalculationResult()

      expect(httpClient.get).toHaveBeenCalledWith('/calculator')
      expect(result).toEqual(mockEmptyResult)
    })

    it('should handle negative values in calculation result', async () => {
      const mockNegativeResult: CalculationResult = {
        xirrs: [
          { date: '2023-01-01', amount: -1000 },
          { date: '2023-02-01', amount: -2000 },
        ],
        median: -8.5,
        average: -10.0,
        total: 2400,
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockNegativeResult,
      } as any)

      const result = await utilityService.getCalculationResult()

      expect(httpClient.get).toHaveBeenCalledWith('/calculator')
      expect(result).toEqual(mockNegativeResult)
    })

    it('should propagate errors on getCalculationResult', async () => {
      const error = new Error('Calculation service unavailable')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(utilityService.getCalculationResult()).rejects.toThrow(
        'Calculation service unavailable'
      )
    })
  })

  describe('getBuildInfo', () => {
    it('should fetch build info', async () => {
      const mockBuildInfo = {
        hash: 'a1b2c3d4e5f6',
        time: '2023-12-31T12:00:00Z',
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockBuildInfo,
      } as any)

      const result = await utilityService.getBuildInfo()

      expect(httpClient.get).toHaveBeenCalledWith('/build-info')
      expect(result).toEqual(mockBuildInfo)
    })

    it('should handle different build info formats', async () => {
      const mockBuildInfo = {
        hash: 'development',
        time: 'local-build',
      }

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockBuildInfo,
      } as any)

      const result = await utilityService.getBuildInfo()

      expect(httpClient.get).toHaveBeenCalledWith('/build-info')
      expect(result).toEqual(mockBuildInfo)
    })

    it('should propagate errors on getBuildInfo', async () => {
      const error = new Error('Build info not available')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(utilityService.getBuildInfo()).rejects.toThrow('Build info not available')
    })
  })
})
