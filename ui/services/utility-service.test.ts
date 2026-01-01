import { describe, it, expect, vi, beforeEach } from 'vitest'
import { utilityService } from './utility-service'
import { httpClient } from '../utils/http-client'
import type { CalculationResult } from '../models/generated/domain-models'

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
        cashFlows: [
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
        cashFlows: [],
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
        cashFlows: [
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

  describe('getLogoUrl', () => {
    it('should return correct logo URL for holdingId', () => {
      const holdingId = 123
      const result = utilityService.getLogoUrl(holdingId)

      expect(result).toBe('/api/logos/123')
    })

    it('should handle different holdingIds', () => {
      expect(utilityService.getLogoUrl(1)).toBe('/api/logos/1')
      expect(utilityService.getLogoUrl(999)).toBe('/api/logos/999')
      expect(utilityService.getLogoUrl(12345)).toBe('/api/logos/12345')
    })
  })

  describe('getLogoUrlByUuid', () => {
    it('should return correct logo URL for UUID', () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000'
      const result = utilityService.getLogoUrlByUuid(uuid)

      expect(result).toBe('/api/logos/uuid/550e8400-e29b-41d4-a716-446655440000')
    })

    it('should handle different UUIDs', () => {
      expect(utilityService.getLogoUrlByUuid('abc-123')).toBe('/api/logos/uuid/abc-123')
      expect(utilityService.getLogoUrlByUuid('test-uuid')).toBe('/api/logos/uuid/test-uuid')
    })
  })
})
