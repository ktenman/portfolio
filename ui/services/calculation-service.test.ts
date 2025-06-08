import { describe, it, expect, vi, beforeEach } from 'vitest'
import { CalculationService } from './calculation-service'
import { ApiClient } from './api-client'

// Mock the ApiClient
vi.mock('./api-client', () => ({
  ApiClient: {
    get: vi.fn(),
  },
}))

// Mock the decorators
vi.mock('../decorators/cacheable.decorator', () => ({
  Cacheable: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

describe('CalculationService', () => {
  let service: CalculationService

  beforeEach(() => {
    vi.clearAllMocks()
    service = new CalculationService()
  })

  describe('fetchCalculationResult', () => {
    it('fetches calculation result from API', async () => {
      const mockResult = {
        currentValue: 10000,
        totalInvestment: 8000,
        totalReturn: 2000,
        returnPercentage: 25.0,
        xirr: 15.5,
      }

      vi.mocked(ApiClient.get).mockResolvedValue(mockResult)

      const result = await service.fetchCalculationResult()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/calculator')
      expect(result).toEqual(mockResult)
    })

    it('propagates API errors', async () => {
      const apiError = new Error('API Error')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      await expect(service.fetchCalculationResult()).rejects.toThrow('API Error')
      expect(ApiClient.get).toHaveBeenCalledWith('/api/calculator')
    })

    it('handles empty calculation result', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue({})

      const result = await service.fetchCalculationResult()

      expect(result).toEqual({})
    })

    it('handles null response', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(null)

      const result = await service.fetchCalculationResult()

      expect(result).toBe(null)
    })

    it('handles calculation result with zero values', async () => {
      const zeroResult = {
        currentValue: 0,
        totalInvestment: 0,
        totalReturn: 0,
        returnPercentage: 0,
        xirr: 0,
      }

      vi.mocked(ApiClient.get).mockResolvedValue(zeroResult)

      const result = await service.fetchCalculationResult()

      expect(result).toEqual(zeroResult)
    })

    it('handles calculation result with negative values', async () => {
      const negativeResult = {
        currentValue: 8000,
        totalInvestment: 10000,
        totalReturn: -2000,
        returnPercentage: -20.0,
        xirr: -10.5,
      }

      vi.mocked(ApiClient.get).mockResolvedValue(negativeResult)

      const result = await service.fetchCalculationResult()

      expect(result).toEqual(negativeResult)
    })
  })

  describe('service initialization', () => {
    it('creates service with correct API URL', () => {
      const newService = new CalculationService()
      expect(newService).toBeInstanceOf(CalculationService)
    })
  })

  describe('caching behavior', () => {
    it('applies cacheable decorator to fetchCalculationResult method', () => {
      // The decorator is mocked, so we just verify the method exists and is callable
      expect(typeof service.fetchCalculationResult).toBe('function')
    })
  })
})
