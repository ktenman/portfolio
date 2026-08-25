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

  describe('coversEveryPlatform', () => {
    it('should cover every platform when all of them are selected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { coversEveryPlatform } = usePlatformFilter('test-key', platforms)
      await nextTick()

      expect(coversEveryPlatform.value).toBe(true)
    })

    it('should not cover every platform when a proper subset is selected', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { coversEveryPlatform, togglePlatform } = usePlatformFilter('test-key', platforms)
      await nextTick()

      togglePlatform('LIGHTYEAR')

      expect(coversEveryPlatform.value).toBe(false)
    })

    it('should cover every platform when the selection is empty', async () => {
      platforms.value = ['LIGHTYEAR', 'TRADING212']
      const { coversEveryPlatform, toggleAllPlatforms } = usePlatformFilter('test-key', platforms)
      await nextTick()

      toggleAllPlatforms()

      expect(coversEveryPlatform.value).toBe(true)
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
