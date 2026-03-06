import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { usePlatformFilter } from './use-platform-filter'

vi.mock('@vueuse/core', () => ({
  useLocalStorage: (_key: string, defaultValue: string[]) => ref([...defaultValue]),
}))

describe('usePlatformFilter', () => {
  const platforms = ref<string[]>([])

  beforeEach(() => {
    platforms.value = []
  })

  describe('initialization', () => {
    it('should select all platforms when available platforms populate', async () => {
      const { selectedPlatforms } = usePlatformFilter('test-key', platforms)
      expect(selectedPlatforms.value).toEqual([])

      platforms.value = ['LIGHTYEAR', 'TRADING212']
      await nextTick()

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR', 'TRADING212'])
    })

    it('should not change selection when available platforms are empty', async () => {
      platforms.value = ['LIGHTYEAR']
      const { selectedPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()

      platforms.value = []
      await nextTick()

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR'])
    })
  })

  describe('isPlatformSelected', () => {
    it('should return true for selected platform', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { isPlatformSelected } = usePlatformFilter('test-key', platforms)
      await nextTick()

      expect(isPlatformSelected('LIGHTYEAR')).toBe(true)
    })

    it('should return false for unselected platform', async () => {
      platforms.value = ['LIGHTYEAR']
      const { selectedPlatforms, isPlatformSelected } = usePlatformFilter('test-key', platforms)
      await nextTick()
      selectedPlatforms.value = []

      expect(isPlatformSelected('BINANCE')).toBe(false)
    })
  })

  describe('togglePlatform', () => {
    it('should remove platform when already selected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { selectedPlatforms, togglePlatform } = usePlatformFilter('test-key', platforms)
      await nextTick()

      togglePlatform('LIGHTYEAR')

      expect(selectedPlatforms.value).toEqual(['TRADING212'])
    })

    it('should add platform when not selected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { selectedPlatforms, togglePlatform } = usePlatformFilter('test-key', platforms)
      await nextTick()
      selectedPlatforms.value = ['LIGHTYEAR']

      togglePlatform('TRADING212')

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR', 'TRADING212'])
    })
  })

  describe('toggleAllPlatforms', () => {
    it('should clear all when all are selected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { selectedPlatforms, toggleAllPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()

      toggleAllPlatforms()

      expect(selectedPlatforms.value).toEqual([])
    })

    it('should select all when some are deselected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212', 'BINANCE']
      const { selectedPlatforms, toggleAllPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()
      selectedPlatforms.value = ['LIGHTYEAR']

      toggleAllPlatforms()

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR', 'TRADING212', 'BINANCE'])
    })
  })

  describe('platform sync', () => {
    it('should remove invalid platforms when available platforms change', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212', 'BINANCE']
      const { selectedPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()

      platforms.value = ['LIGHTYEAR', 'TRADING212']
      await nextTick()

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR', 'TRADING212'])
    })

    it('should select all when all selected platforms become invalid', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { selectedPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()
      selectedPlatforms.value = ['BINANCE']

      platforms.value = ['LIGHTYEAR', 'TRADING212']
      await nextTick()

      expect(selectedPlatforms.value).toEqual(['LIGHTYEAR', 'TRADING212'])
    })
  })
})
