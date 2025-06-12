import { cacheService } from '../services/cache-service'

export async function withCache<T>(key: string, fetcher: () => Promise<T | void>): Promise<T> {
  const cached = cacheService.getItem<T>(key)
  if (cached) return cached

  const result = await fetcher()
  if (result !== undefined && result !== null) {
    cacheService.setItem(key, result)
    return result as T
  }
  throw new Error('No data returned from fetcher')
}
