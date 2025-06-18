import { describe, it, expect, vi, beforeEach } from 'vitest'
import { enumService } from '../services/enum-service'

vi.mock('../services/enum-service')

describe('useEnumValues', () => {
  const mockEnumData = {
    platforms: ['DEGIRO', 'TRADING212', 'BINANCE'],
    providers: ['ALPHA_VANTAGE', 'BINANCE', 'FT'],
    transactionTypes: ['BUY', 'SELL'],
    categories: ['STOCK', 'ETF', 'CRYPTO'],
    currencies: ['EUR', 'USD', 'GBP'],
  }

  let useEnumValues: any

  beforeEach(async () => {
    vi.clearAllMocks()
    // Reset the module to clear the cached ref
    vi.resetModules()
    // Re-import to get fresh module state
    const module = await import('./use-enum-values')
    useEnumValues = module.useEnumValues
  })

  describe('initial state', () => {
    it('should have empty options and false loading state initially', () => {
      const {
        loading,
        error,
        platformOptions,
        providerOptions,
        transactionTypeOptions,
        categoryOptions,
        currencyOptions,
      } = useEnumValues()

      expect(loading.value).toBe(false)
      expect(error.value).toBeNull()
      expect(platformOptions.value).toEqual([])
      expect(providerOptions.value).toEqual([])
      expect(transactionTypeOptions.value).toEqual([])
      expect(categoryOptions.value).toEqual([])
      expect(currencyOptions.value).toEqual([])
    })
  })

  describe('loadAll', () => {
    it('should load enum values and set loading states correctly', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)

      const { loadAll, loading, error, platformOptions } = useEnumValues()

      // Start loading
      const loadPromise = loadAll()
      expect(loading.value).toBe(true)
      expect(error.value).toBeNull()

      // Wait for completion
      await loadPromise

      expect(loading.value).toBe(false)
      expect(error.value).toBeNull()
      expect(enumService.getAll).toHaveBeenCalledTimes(1)
      expect(platformOptions.value).toHaveLength(3)
    })

    it('should handle errors and set error state', async () => {
      const mockError = new Error('Network error')
      vi.mocked(enumService.getAll).mockRejectedValue(mockError)

      const { loadAll, loading, error, platformOptions } = useEnumValues()

      await loadAll()

      expect(loading.value).toBe(false)
      expect(error.value).toBe(mockError)
      expect(platformOptions.value).toEqual([])
    })

    it('should cache results and not call service again', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)

      const { loadAll } = useEnumValues()

      // First call
      await loadAll()
      expect(enumService.getAll).toHaveBeenCalledTimes(1)

      // Second call - should use cache
      await loadAll()
      expect(enumService.getAll).toHaveBeenCalledTimes(1)
    })

    it('should share cache between multiple composable instances', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)

      const instance1 = useEnumValues()
      await instance1.loadAll()

      const instance2 = useEnumValues()
      await instance2.loadAll()

      // Service should only be called once due to shared cache
      expect(enumService.getAll).toHaveBeenCalledTimes(1)
      expect(instance2.platformOptions.value).toHaveLength(3)
    })
  })

  describe('data transformation', () => {
    beforeEach(async () => {
      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)
      const { loadAll } = useEnumValues()
      await loadAll()
    })

    it('should transform platform options correctly', () => {
      const { platformOptions } = useEnumValues()

      expect(platformOptions.value).toEqual([
        { value: 'DEGIRO', text: 'Degiro' },
        { value: 'TRADING212', text: 'Trading212' },
        { value: 'BINANCE', text: 'Binance' },
      ])
    })

    it('should transform provider options correctly', () => {
      const { providerOptions } = useEnumValues()

      expect(providerOptions.value).toEqual([
        { value: 'ALPHA_VANTAGE', text: 'Alpha vantage' },
        { value: 'BINANCE', text: 'Binance' },
        { value: 'FT', text: 'Ft' },
      ])
    })

    it('should transform transaction type options with custom mapping', () => {
      const { transactionTypeOptions } = useEnumValues()

      expect(transactionTypeOptions.value).toEqual([
        { value: 'BUY', text: 'Buy' },
        { value: 'SELL', text: 'Sell' },
      ])
    })

    it('should transform category options correctly', () => {
      const { categoryOptions } = useEnumValues()

      expect(categoryOptions.value).toEqual([
        { value: 'STOCK', text: 'Stock' },
        { value: 'ETF', text: 'Etf' },
        { value: 'CRYPTO', text: 'Crypto' },
      ])
    })

    it('should transform currency options correctly', () => {
      const { currencyOptions } = useEnumValues()

      expect(currencyOptions.value).toEqual([
        { value: 'EUR', text: 'Eur' },
        { value: 'USD', text: 'Usd' },
        { value: 'GBP', text: 'Gbp' },
      ])
    })

    it('should handle underscores in enum values', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        ...mockEnumData,
        platforms: ['TEST_PLATFORM_NAME'],
      })

      // Need a fresh instance to get the new data
      vi.resetModules()
      const freshUseEnumValues = (await import('./use-enum-values')).useEnumValues
      const { loadAll, platformOptions } = freshUseEnumValues()
      await loadAll()

      expect(platformOptions.value).toEqual([
        { value: 'TEST_PLATFORM_NAME', text: 'Test platform name' },
      ])
    })
  })

  describe('computed properties reactivity', () => {
    it('should return empty arrays when cache is null', () => {
      const {
        platformOptions,
        providerOptions,
        transactionTypeOptions,
        categoryOptions,
        currencyOptions,
      } = useEnumValues()

      expect(platformOptions.value).toEqual([])
      expect(providerOptions.value).toEqual([])
      expect(transactionTypeOptions.value).toEqual([])
      expect(categoryOptions.value).toEqual([])
      expect(currencyOptions.value).toEqual([])
    })

    it('should update computed properties when cache is populated', async () => {
      const { loadAll, platformOptions } = useEnumValues()

      expect(platformOptions.value).toEqual([])

      vi.mocked(enumService.getAll).mockResolvedValue(mockEnumData)
      await loadAll()

      expect(platformOptions.value).toHaveLength(3)
    })
  })

  describe('edge cases', () => {
    it('should handle empty arrays in enum data', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        platforms: [],
        providers: [],
        transactionTypes: [],
        categories: [],
        currencies: [],
      })

      const {
        loadAll,
        platformOptions,
        providerOptions,
        transactionTypeOptions,
        categoryOptions,
        currencyOptions,
      } = useEnumValues()
      await loadAll()

      expect(platformOptions.value).toEqual([])
      expect(providerOptions.value).toEqual([])
      expect(transactionTypeOptions.value).toEqual([])
      expect(categoryOptions.value).toEqual([])
      expect(currencyOptions.value).toEqual([])
    })

    it('should handle single character enum values', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        ...mockEnumData,
        platforms: ['A', 'B'],
      })

      vi.resetModules()
      const freshUseEnumValues = (await import('./use-enum-values')).useEnumValues
      const { loadAll, platformOptions } = freshUseEnumValues()
      await loadAll()

      expect(platformOptions.value).toEqual([
        { value: 'A', text: 'A' },
        { value: 'B', text: 'B' },
      ])
    })

    it('should handle mixed case enum values', async () => {
      vi.mocked(enumService.getAll).mockResolvedValue({
        ...mockEnumData,
        platforms: ['MixedCase', 'UPPERCASE', 'lowercase'],
      })

      vi.resetModules()
      const freshUseEnumValues = (await import('./use-enum-values')).useEnumValues
      const { loadAll, platformOptions } = freshUseEnumValues()
      await loadAll()

      expect(platformOptions.value).toEqual([
        { value: 'MixedCase', text: 'Mixedcase' },
        { value: 'UPPERCASE', text: 'Uppercase' },
        { value: 'lowercase', text: 'lowercase' }, // lowercase doesn't get capitalized
      ])
    })
  })
})
