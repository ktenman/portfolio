import { beforeEach, describe, expect, it, vi } from 'vitest'
import { enumService } from '../services/enum-service'

vi.mock('../services/enum-service')

describe('useEnumValues', () => {
  const mockEnumData = {
    platforms: ['DEGIRO', 'TRADING212', 'BINANCE'],
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
        { value: 'TRADING212', text: 'Trading212' },
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

    it('should handle special formatting for underscored values', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        ...mockEnumData,
        platforms: ['TEST_PLATFORM_NAME', 'ANOTHER_TEST'],
      })

      vi.resetModules()
      const freshUseEnumValues = (await import('./use-enum-values')).useEnumValues
      const { loadAll, platformOptions } = freshUseEnumValues()
      await loadAll()

      // NOTE: Business requirement - format underscored enum values for display
      expect(platformOptions.value).toEqual([
        { value: 'TEST_PLATFORM_NAME', text: 'Test Platform Name' },
        { value: 'ANOTHER_TEST', text: 'Another Test' },
      ])
    })
  })
})
