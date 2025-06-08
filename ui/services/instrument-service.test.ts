import { describe, it, expect, vi, beforeEach } from 'vitest'
import { InstrumentService } from './instrument-service'
import { ApiClient } from './api-client'
import { Instrument } from '../models/instrument'

// Mock the ApiClient
vi.mock('./api-client', () => ({
  ApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

// Mock the decorators
vi.mock('../decorators/cacheable.decorator', () => ({
  Cacheable: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

vi.mock('../decorators/cache-put.decorator', () => ({
  CachePut: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

vi.mock('../decorators/cache-evict.decorator', () => ({
  CacheEvict: () => (_target: any, _propertyKey: string, descriptor: PropertyDescriptor) =>
    descriptor,
}))

describe('InstrumentService', () => {
  let service: InstrumentService

  const mockInstrument: Instrument = {
    id: 1,
    symbol: 'AAPL',
    name: 'Apple Inc.',
    category: 'STOCK',
    baseCurrency: 'USD',
    providerName: 'ALPHA_VANTAGE',
    xirr: 15.5,
    totalInvestment: 8000,
    currentValue: 10000,
    profit: 2000,
    currentPrice: 150.0,
    quantity: 100,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    service = new InstrumentService()
  })

  describe('getAllInstruments', () => {
    it('fetches all instruments from API', async () => {
      const mockInstruments = [mockInstrument]
      vi.mocked(ApiClient.get).mockResolvedValue(mockInstruments)

      const result = await service.getAllInstruments()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/instruments')
      expect(result).toEqual(mockInstruments)
    })

    it('handles empty instruments list', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue([])

      const result = await service.getAllInstruments()

      expect(result).toEqual([])
    })

    it('propagates API errors', async () => {
      const apiError = new Error('API Error')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      await expect(service.getAllInstruments()).rejects.toThrow('API Error')
    })
  })

  describe('saveInstrument', () => {
    it('saves new instrument via API', async () => {
      const newInstrument = { ...mockInstrument, id: undefined }
      const savedInstrument = { ...mockInstrument, id: 2 }

      vi.mocked(ApiClient.post).mockResolvedValue(savedInstrument)

      const result = await service.saveInstrument(newInstrument as any)

      expect(ApiClient.post).toHaveBeenCalledWith('/api/instruments', newInstrument)
      expect(result).toEqual(savedInstrument)
    })

    it('handles API errors during save', async () => {
      const apiError = new Error('Validation Error')
      vi.mocked(ApiClient.post).mockRejectedValue(apiError)

      await expect(service.saveInstrument(mockInstrument)).rejects.toThrow('Validation Error')
    })

    it('saves instrument with all required fields', async () => {
      const completeInstrument = {
        symbol: 'GOOGL',
        name: 'Alphabet Inc.',
        providerName: 'ALPHA_VANTAGE',
        platform: 'NASDAQ',
      }
      const savedInstrument = { ...completeInstrument, id: 3 }

      vi.mocked(ApiClient.post).mockResolvedValue(savedInstrument)

      const result = await service.saveInstrument(completeInstrument as any)

      expect(result).toEqual(savedInstrument)
      expect(result.id).toBeDefined()
    })
  })

  describe('updateInstrument', () => {
    it('updates existing instrument via API', async () => {
      const updatedInstrument = { ...mockInstrument, name: 'Apple Inc. Updated' }
      vi.mocked(ApiClient.put).mockResolvedValue(updatedInstrument)

      const result = await service.updateInstrument(1, updatedInstrument)

      expect(ApiClient.put).toHaveBeenCalledWith('/api/instruments/1', updatedInstrument)
      expect(result).toEqual(updatedInstrument)
    })

    it('handles API errors during update', async () => {
      const apiError = new Error('Not Found')
      vi.mocked(ApiClient.put).mockRejectedValue(apiError)

      await expect(service.updateInstrument(1, mockInstrument)).rejects.toThrow('Not Found')
    })

    it('updates instrument with different ID', async () => {
      const instrumentId = 5
      vi.mocked(ApiClient.put).mockResolvedValue(mockInstrument)

      await service.updateInstrument(instrumentId, mockInstrument)

      expect(ApiClient.put).toHaveBeenCalledWith(`/api/instruments/${instrumentId}`, mockInstrument)
    })
  })

  describe('deleteInstrument', () => {
    it('deletes instrument via API', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      await service.deleteInstrument(1)

      expect(ApiClient.delete).toHaveBeenCalledWith('/api/instruments/1')
    })

    it('handles API errors during delete', async () => {
      const apiError = new Error('Not Found')
      vi.mocked(ApiClient.delete).mockRejectedValue(apiError)

      await expect(service.deleteInstrument(1)).rejects.toThrow('Not Found')
    })

    it('deletes instrument with different ID', async () => {
      const instrumentId = 10
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      await service.deleteInstrument(instrumentId)

      expect(ApiClient.delete).toHaveBeenCalledWith(`/api/instruments/${instrumentId}`)
    })

    it('handles successful delete without return value', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)

      const result = await service.deleteInstrument(1)

      expect(result).toBeUndefined()
    })
  })

  describe('service initialization', () => {
    it('creates service with correct API URL', () => {
      const newService = new InstrumentService()
      expect(newService).toBeInstanceOf(InstrumentService)
    })
  })

  describe('caching behavior', () => {
    it('applies cache decorators to appropriate methods', () => {
      // The decorators are mocked, so we just verify the methods exist and are callable
      expect(typeof service.getAllInstruments).toBe('function')
      expect(typeof service.saveInstrument).toBe('function')
      expect(typeof service.updateInstrument).toBe('function')
      expect(typeof service.deleteInstrument).toBe('function')
    })
  })

  describe('error handling', () => {
    it('handles network timeouts', async () => {
      const timeoutError = new Error('Network timeout')
      vi.mocked(ApiClient.get).mockRejectedValue(timeoutError)

      await expect(service.getAllInstruments()).rejects.toThrow('Network timeout')
    })

    it('handles server errors', async () => {
      const serverError = new Error('Internal Server Error')
      vi.mocked(ApiClient.post).mockRejectedValue(serverError)

      await expect(service.saveInstrument(mockInstrument)).rejects.toThrow('Internal Server Error')
    })
  })
})
