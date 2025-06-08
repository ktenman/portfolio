import { describe, it, expect, beforeEach, vi } from 'vitest'
import { CacheService } from './cache-service'
import { CACHE_KEYS } from '../constants/cache-keys'
import { APP_CONFIG } from '../constants/app-config'

describe('CacheService', () => {
  let cacheService: CacheService
  let mockStorage: Record<string, string>

  beforeEach(() => {
    mockStorage = {}

    vi.mocked(localStorage.getItem).mockImplementation(key => mockStorage[key] || null)
    vi.mocked(localStorage.setItem).mockImplementation((key, value) => {
      mockStorage[key] = value
    })
    vi.mocked(localStorage.removeItem).mockImplementation(key => {
      delete mockStorage[key]
    })

    vi.spyOn(Date, 'now').mockReturnValue(1000000)

    cacheService = new CacheService()
  })

  describe('setItem', () => {
    it('stores data with timestamp', () => {
      const data = { id: 1, name: 'Test' }
      cacheService.setItem('test-key', data)

      const stored = JSON.parse(mockStorage['test-key'])
      expect(stored).toEqual({
        timestamp: 1000000,
        data: { id: 1, name: 'Test' },
      })
    })

    it('overwrites existing data', () => {
      cacheService.setItem('test-key', { value: 'old' })
      cacheService.setItem('test-key', { value: 'new' })

      const stored = JSON.parse(mockStorage['test-key'])
      expect(stored.data).toEqual({ value: 'new' })
    })
  })

  describe('getItem', () => {
    it('returns null for non-existent keys', () => {
      expect(cacheService.getItem('non-existent')).toBeNull()
    })

    it('returns data if cache is still valid', () => {
      const data = { id: 1, name: 'Test' }
      const cacheContent = {
        timestamp: Date.now(),
        data,
      }
      mockStorage['test-key'] = JSON.stringify(cacheContent)

      expect(cacheService.getItem('test-key')).toEqual(data)
    })

    it('returns null and removes item if cache is expired', () => {
      const data = { id: 1, name: 'Test' }
      const expiredTimestamp = Date.now() - APP_CONFIG.CACHE_VALIDITY_MS - 1000
      const cacheContent = {
        timestamp: expiredTimestamp,
        data,
      }
      mockStorage['test-key'] = JSON.stringify(cacheContent)

      expect(cacheService.getItem('test-key')).toBeNull()
      expect(mockStorage['test-key']).toBeUndefined()
    })

    it('handles edge case of exactly at expiration time', () => {
      const data = { id: 1, name: 'Test' }
      const edgeTimestamp = Date.now() - APP_CONFIG.CACHE_VALIDITY_MS
      const cacheContent = {
        timestamp: edgeTimestamp,
        data,
      }
      mockStorage['test-key'] = JSON.stringify(cacheContent)

      expect(cacheService.getItem('test-key')).toBeNull()
      expect(mockStorage['test-key']).toBeUndefined()
    })
  })

  describe('clearItem', () => {
    it('removes item from localStorage', () => {
      mockStorage['test-key'] = 'some-value'
      cacheService.clearItem('test-key')

      expect(mockStorage['test-key']).toBeUndefined()
    })

    it('handles clearing non-existent items', () => {
      expect(() => cacheService.clearItem('non-existent')).not.toThrow()
    })
  })

  describe('clearAllSummaryCaches', () => {
    it('removes all summary cache keys', () => {
      mockStorage[CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT] = 'current-data'
      mockStorage[CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL] = 'historical-data'
      mockStorage['other-key'] = 'other-data'

      cacheService.clearAllSummaryCaches()

      expect(mockStorage[CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT]).toBeUndefined()
      expect(mockStorage[CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL]).toBeUndefined()
      expect(mockStorage['other-key']).toBe('other-data')
    })
  })
})
