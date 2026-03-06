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

      const { loadAll, platformOptions, transactionTypeOptions } = useEnumValues()
      await loadAll()

      // NOTE: Testing the business logic of enum transformation
      expect(platformOptions.value).toEqual([
        { value: 'DEGIRO', text: 'Degiro' },
        { value: 'TRADING212', text: 'Trading 212' },
        { value: 'BINANCE', text: 'Binance' },
      ])

      expect(transactionTypeOptions.value).toEqual([
        { value: 'BUY', text: 'Buy' },
        { value: 'SELL', text: 'Sell' },
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

    it('should use display names from PlatformDto', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        ...mockEnumData,
        platforms: [
          { name: 'TEST_PLATFORM', displayName: 'Test Platform' },
          { name: 'ANOTHER_TEST', displayName: 'Another Test' },
        ],
      })

      vi.resetModules()
      const freshUseEnumValues = (await import('./use-enum-values')).useEnumValues
      const { loadAll, platformOptions } = freshUseEnumValues()
      await loadAll()

      expect(platformOptions.value).toEqual([
        { value: 'TEST_PLATFORM', text: 'Test Platform' },
        { value: 'ANOTHER_TEST', text: 'Another Test' },
      ])
    })
  })
})
