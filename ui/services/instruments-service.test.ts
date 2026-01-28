import { describe, it, expect, vi, beforeEach } from 'vitest'
import { instrumentsService } from './instruments-service'
import { httpClient } from '../utils/http-client'
import { PriceChangePeriod, ProviderName } from '../models/generated/domain-models'
import { createInstrumentDto } from '../tests/fixtures'

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
      const mockInstruments = [
        createInstrumentDto({
          id: 1,
          symbol: 'AAPL',
          name: 'Apple Inc.',
          providerName: ProviderName.FT,
          category: 'STOCK',
        }),
        createInstrumentDto({
          id: 2,
          symbol: 'BTC',
          name: 'Bitcoin',
          providerName: ProviderName.BINANCE,
          category: 'CRYPTO',
        }),
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce(mockInstruments)

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { period: PriceChangePeriod.P24H },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle empty response', async () => {
      vi.mocked(httpClient.get).mockResolvedValueOnce([])

      const result = await instrumentsService.getAll()

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { period: PriceChangePeriod.P24H },
      })
      expect(result).toEqual([])
    })

    it('should propagate errors', async () => {
      const error = new Error('Network error')
      vi.mocked(httpClient.get).mockRejectedValueOnce(error)

      await expect(instrumentsService.getAll()).rejects.toThrow('Network error')
    })

    it('should fetch instruments with platform filter', async () => {
      const mockInstruments = [
        createInstrumentDto({
          id: 1,
          symbol: 'AAPL',
          name: 'Apple Inc.',
          providerName: ProviderName.FT,
          category: 'STOCK',
          platforms: ['TRADING212'],
        }),
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce(mockInstruments)

      const result = await instrumentsService.getAll(['TRADING212'])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { period: PriceChangePeriod.P24H, platforms: ['TRADING212'] },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle undefined platform parameter', async () => {
      const mockInstruments: never[] = []

      vi.mocked(httpClient.get).mockResolvedValueOnce(mockInstruments)

      const result = await instrumentsService.getAll(undefined)

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { period: PriceChangePeriod.P24H },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should fetch instruments with multiple platforms', async () => {
      const mockInstruments = [
        createInstrumentDto({
          id: 1,
          symbol: 'MULTI',
          name: 'Multi Platform',
          providerName: ProviderName.FT,
          platforms: ['TRADING212', 'BINANCE', 'COINBASE'],
        }),
      ]
      vi.mocked(httpClient.get).mockResolvedValueOnce(mockInstruments)

      const result = await instrumentsService.getAll(['TRADING212', 'BINANCE', 'COINBASE'])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: {
          period: PriceChangePeriod.P24H,
          platforms: ['TRADING212', 'BINANCE', 'COINBASE'],
        },
      })
      expect(result).toEqual(mockInstruments)
    })

    it('should handle empty array for platforms', async () => {
      const mockInstruments: never[] = []

      vi.mocked(httpClient.get).mockResolvedValueOnce(mockInstruments)

      const result = await instrumentsService.getAll([])

      expect(httpClient.get).toHaveBeenCalledWith('/instruments', {
        params: { period: PriceChangePeriod.P24H },
      })
      expect(result).toEqual(mockInstruments)
    })
  })

  describe('create', () => {
    it('should create a new instrument', async () => {
      const newInstrument = {
        symbol: 'GOOGL',
        name: 'Alphabet Inc.',
        providerName: ProviderName.FT,
        category: 'STOCK',
      }

      const createdInstrument = createInstrumentDto({
        id: 3,
        ...newInstrument,
      })

      vi.mocked(httpClient.post).mockResolvedValueOnce(createdInstrument)

      const result = await instrumentsService.create(newInstrument)

      expect(httpClient.post).toHaveBeenCalledWith('/instruments', newInstrument)
      expect(result).toEqual(createdInstrument)
    })

    it('should handle partial instrument data', async () => {
      const partialInstrument = {
        symbol: 'ETH',
        name: 'Ethereum',
      }

      const createdInstrument = createInstrumentDto({
        id: 4,
        symbol: 'ETH',
        name: 'Ethereum',
        providerName: ProviderName.BINANCE,
        category: 'CRYPTO',
      })

      vi.mocked(httpClient.post).mockResolvedValueOnce(createdInstrument)

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
      const updateData = {
        name: 'Updated Name',
      }

      const updatedInstrument = createInstrumentDto({
        id: 1,
        symbol: 'AAPL',
        name: 'Updated Name',
        providerName: ProviderName.FT,
        category: 'STOCK',
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce(updatedInstrument)

      const result = await instrumentsService.update(1, updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/1', updateData)
      expect(result).toEqual(updatedInstrument)
    })

    it('should handle empty update data', async () => {
      const updatedInstrument = createInstrumentDto({
        id: 15,
        symbol: 'EMPTY',
        name: 'Empty Update',
        providerName: ProviderName.FT,
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce(updatedInstrument)

      const result = await instrumentsService.update(15, {})

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/15', {})
      expect(result).toEqual(updatedInstrument)
    })

    it('should update an instrument by string id', async () => {
      const updateData = {
        currentPrice: 45000,
      }

      const updatedInstrument = createInstrumentDto({
        id: 2,
        symbol: 'BTC',
        name: 'Bitcoin',
        providerName: ProviderName.BINANCE,
        category: 'CRYPTO',
        currentPrice: 45000,
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce(updatedInstrument)

      const result = await instrumentsService.update('2', updateData)

      expect(httpClient.put).toHaveBeenCalledWith('/instruments/2', updateData)
      expect(result).toEqual(updatedInstrument)
    })

    it('should handle full instrument update', async () => {
      const fullUpdate = {
        symbol: 'MSFT',
        name: 'Microsoft Corporation',
        providerName: ProviderName.FT,
        category: 'STOCK',
      }

      const updatedInstrument = createInstrumentDto({
        id: 5,
        ...fullUpdate,
      })

      vi.mocked(httpClient.put).mockResolvedValueOnce(updatedInstrument)

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
