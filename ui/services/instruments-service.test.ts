import { describe, it, expect, vi, beforeEach } from 'vitest'
import { instrumentsService } from './instruments-service'
import { httpClient } from '../utils/http-client'
import type { Instrument } from '../models/instrument'
import { ProviderName } from '../models/provider-name'

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
          providerName: ProviderName.ALPHA_VANTAGE,
          type: 'STOCK',
        },
        {
          id: 2,
          symbol: 'BTC',
          name: 'Bitcoin',
          providerName: ProviderName.BINANCE,
          type: 'CRYPTO',
        },
      ]

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      })

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', { params: {} })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle empty response', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: [],
      } as any)

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', { params: {} })
      expect(result).toEqual([])
    })

    it('should propagate errors', async () => {
      const error = new Error('Network error')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(instrumentsService.getAll()).rejects.toThrow('Network error')
    })

    it('should fetch instruments with platform filter', async () => {
      const mockInstruments: Instrument[] = [
        {
          id: 1,
          symbol: 'AAPL',
          name: 'Apple Inc.',
          providerName: ProviderName.ALPHA_VANTAGE,
          type: 'STOCK',
          platforms: ['TRADING212'],
        },
      ]

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      })

      const result = await instrumentsService.getAll(['TRADING212'])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { platforms: ['TRADING212'] },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle undefined platform parameter', async () => {
      const mockInstruments: Instrument[] = []

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      })

      const result = await instrumentsService.getAll(undefined)

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', { params: {} })
      expect(result).toEqual(mockInstruments)
    })

    it('should fetch instruments with multiple platforms', async () => {
      const mockInstruments: Instrument[] = [
        {
          id: 1,
          symbol: 'MULTI',
          name: 'Multi Platform',
          providerName: ProviderName.FT,
          platforms: ['TRADING212', 'BINANCE', 'COINBASE'],
        },
      ]

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      })

      const result = await instrumentsService.getAll(['TRADING212', 'BINANCE', 'COINBASE'])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { platforms: ['TRADING212', 'BINANCE', 'COINBASE'] },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle empty array for platforms', async () => {
      const mockInstruments: Instrument[] = []

      vi.mocked(httpClient.get).mockResolvedValueOnce({
        data: mockInstruments,
      })

      const result = await instrumentsService.getAll([])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', { params: {} })
      expect(result).toEqual(mockInstruments)
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

    it('should handle empty update data', async () => {
      const updatedInstrument: Instrument = {
        id: 15,
        symbol: 'EMPTY',
        name: 'Empty Update',
        providerName: ProviderName.FT,
      }

      vi.mocked(httpClient.put).mockResolvedValueOnce({
        data: updatedInstrument,
      })

      const result = await instrumentsService.update(15, {})

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/15', {})
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
        providerName: ProviderName.FT,
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
