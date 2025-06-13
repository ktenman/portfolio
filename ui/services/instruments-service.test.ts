import { describe, it, expect, vi, beforeEach } from 'vitest'
import { instrumentsService } from './instruments-service'
import { httpClient } from '../utils/http-client'
import type { Instrument } from '../models/instrument'

vi.mock('../utils/http-client', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('instrumentsService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getAll', () => {
    it('should fetch all instruments', async () => {
      const mockInstruments: Instrument[] = [
        {
          id: 1,
          symbol: 'AAPL',
          name: 'Apple Inc.',
          providerName: 'ALPHA_VANTAGE' as any,
          type: 'STOCK',
        },
        {
          id: 2,
          symbol: 'BTC',
          name: 'Bitcoin',
          providerName: 'BINANCE' as any,
          type: 'CRYPTO',
        },
      ]

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      } as any)

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments')
      expect(result).toEqual(mockInstruments)
    })

    it('should handle empty response', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: [],
      } as any)

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments')
      expect(result).toEqual([])
    })

    it('should propagate errors', async () => {
      const error = new Error('Network error')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(instrumentsService.getAll()).rejects.toThrow('Network error')
    })
  })

  describe('create', () => {
    it('should create a new instrument', async () => {
      const newInstrument: Partial<Instrument> = {
        symbol: 'GOOGL',
        name: 'Alphabet Inc.',
        providerName: 'ALPHA_VANTAGE' as any,
        type: 'STOCK',
      }

      const createdInstrument: Instrument = {
        id: 3,
        ...newInstrument,
      } as Instrument

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: createdInstrument,
      } as any)

      const result = await instrumentsService.create(newInstrument)

      expect(httpClient.post).toHaveBeenCalledWith('/instruments', newInstrument)
      expect(result).toEqual(createdInstrument)
    })

    it('should handle partial instrument data', async () => {
      const partialInstrument: Partial<Instrument> = {
        symbol: 'ETH',
        name: 'Ethereum',
      }

      const createdInstrument: Instrument = {
        id: 4,
        symbol: 'ETH',
        name: 'Ethereum',
        providerName: 'BINANCE' as any,
        type: 'CRYPTO',
      }

      vi.mocked(httpClient.post).mockResolvedValueOnce({
        data: createdInstrument,
      } as any)

      const result = await instrumentsService.create(partialInstrument)

      expect(httpClient.post).toHaveBeenCalledWith('/instruments', partialInstrument)
      expect(result).toEqual(createdInstrument)
    })

    it('should propagate errors on create', async () => {
      const error = new Error('Validation error')
      vi.mocked(httpClient.post).mockRejectedValueOnce(error)

      await expect(instrumentsService.create({ symbol: 'INVALID' })).rejects.toThrow(
        'Validation error'
      )
    })
  })

  describe('update', () => {
    it('should update an instrument by numeric id', async () => {
      const updateData: Partial<Instrument> = {
        name: 'Updated Name',
      }

      const updatedInstrument: Instrument = {
        id: 1,
        symbol: 'AAPL',
        name: 'Updated Name',
        providerName: 'ALPHA_VANTAGE' as any,
        type: 'STOCK',
      }

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedInstrument,
      } as any)

      const result = await instrumentsService.update(1, updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/1', updateData)
      expect(result).toEqual(updatedInstrument)
    })

    it('should update an instrument by string id', async () => {
      const updateData: Partial<Instrument> = {
        currentPrice: 45000,
      }

      const updatedInstrument: Instrument = {
        id: 2,
        symbol: 'BTC',
        name: 'Bitcoin',
        providerName: 'BINANCE' as any,
        type: 'CRYPTO',
        currentPrice: 45000,
      }

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedInstrument,
      } as any)

      const result = await instrumentsService.update('2', updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/2', updateData)
      expect(result).toEqual(updatedInstrument)
    })

    it('should handle full instrument update', async () => {
      const fullUpdate: Partial<Instrument> = {
        symbol: 'MSFT',
        name: 'Microsoft Corporation',
        providerName: 'FINANCIAL_TIMES' as any,
        type: 'STOCK',
      }

      const updatedInstrument: Instrument = {
        id: 5,
        ...fullUpdate,
      } as Instrument

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedInstrument,
      } as any)

      const result = await instrumentsService.update(5, fullUpdate)

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/5', fullUpdate)
      expect(result).toEqual(updatedInstrument)
    })

    it('should propagate errors on update', async () => {
      const error = new Error('Not found')
      vi.mocked(httpClient.put).mockRejectedValueOnce(error)

      await expect(instrumentsService.update(999, { name: 'Test' })).rejects.toThrow('Not found')
    })
  })
})
