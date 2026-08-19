import { beforeEach, describe, expect, it, vi } from 'vitest'
import { enumService } from '../services/enum-service'

vi.mock('../services/enum-service')

describe('useEnumValues', () => {
  const mockEnumData = {
    platforms: [
      { name: 'DEGIRO', displayName: 'Degiro' },
      { name: 'TRADING212', displayName: 'Trading 212' },
      { name: 'BINANCE', displayName: 'Binance' },
    ],
    providers: ['BINANCE', 'FT', 'LIGHTYEAR', 'TRADING212'],
    transactionTypes: ['BUY', 'SELL'],
    categories: ['STOCK', 'ETF', 'CRYPTO'],
    currencies: ['EUR', 'USD', 'GBP'],
  }

  let useEnumValues: any

  beforeEach(async () => {
    vi.clearAllMocks()
    vi.resetModules()
    const module = await import('./use-enum-values')
    useEnumValues = module.useEnumValues
  })

  describe('core functionality', () => {
    it('should load and transform enum values correctly', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)

      const { loadAll, providerOptions, categoryOptions } = useEnumValues()
      await loadAll()

      expect(providerOptions.value).toEqual([
        { value: 'BINANCE', text: 'Binance' },
        { value: 'FT', text: 'FT' },
        { value: 'LIGHTYEAR', text: 'Lightyear' },
        { value: 'TRADING212', text: 'Trading212' },
      ])

      expect(categoryOptions.value).toEqual([
        { value: 'STOCK', text: 'Stock' },
        { value: 'ETF', text: 'ETF' },
        { value: 'CRYPTO', text: 'Crypto' },
      ])
    })

    it('should handle errors gracefully', async () => {
      const mockError = new Error('Network error')
      vi.mocked(enumService.getAll).mockRejectedValue(mockError)

      const { loadAll, loading, error } = useEnumValues()
      await loadAll()

      expect(loading.value).toBe(false)
      expect(error.value).toBe(mockError)
    })

    it('should cache results to avoid duplicate API calls', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)

      const { loadAll } = useEnumValues()
      await loadAll()
      await loadAll()

      // NOTE: Important business logic - caching prevents unnecessary API calls
      expect(enumService.getAll).toHaveBeenCalledTimes(1)
    })
  })
})
