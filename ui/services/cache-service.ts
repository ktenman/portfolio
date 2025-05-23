import { CACHE_KEYS } from '../constants/cache-keys'

type CacheContent<T> = {
  timestamp: number
  data: T
}

export class CacheService {
  private readonly cacheValidity: number = 60_000 // 1 minute

  setItem<T>(key: string, data: T): void {
    const cacheContent: CacheContent<T> = { timestamp: Date.now(), data }
    localStorage.setItem(key, JSON.stringify(cacheContent))
  }

  getItem<T>(key: string): T | null {
    const item = localStorage.getItem(key)
    if (!item) return null

    const cacheContent: CacheContent<T> = JSON.parse(item)
    if (Date.now() - cacheContent.timestamp < this.cacheValidity) {
      return cacheContent.data
    }

    localStorage.removeItem(key)
    return null
  }

  clearItem(key: string): void {
    localStorage.removeItem(key)
  }

  clearAllSummaryCaches(): void {
    const summaryKeys = [
      CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT,
      CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL,
    ]

    summaryKeys.forEach(key => this.clearItem(key))
  }
}
