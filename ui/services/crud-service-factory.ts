import { apiClient } from './api-client'
import { cacheService } from './cache-service'
import { ICrudService } from '../types/service-interfaces'
import { withCache } from '../utils/cache-utils'

class GenericCrudService<T extends { id?: number | string }> implements ICrudService<T> {
  constructor(
    private readonly baseUrl: string,
    private readonly cacheKey: string
  ) {}

  async getAll(): Promise<T[]> {
    return withCache(this.cacheKey, () => apiClient.get<T[]>(this.baseUrl))
  }

  async create(data: Partial<T>): Promise<T> {
    const result = await apiClient.post<T>(this.baseUrl, data)
    cacheService.clearItem(this.cacheKey)
    if (!result) throw new Error('No data returned from create')
    return result
  }

  async update(id: number | string, data: Partial<T>): Promise<T> {
    const result = await apiClient.put<T>(`${this.baseUrl}/${id}`, data)
    cacheService.clearItem(this.cacheKey)
    if (!result) throw new Error('No data returned from update')
    return result
  }

  async delete(id: number | string): Promise<void> {
    await apiClient.delete(`${this.baseUrl}/${id}`)
    cacheService.clearItem(this.cacheKey)
  }
}

export function createCrudService<T extends { id?: number | string }>(
  baseUrl: string,
  cacheKey: string
): ICrudService<T> {
  return new GenericCrudService<T>(baseUrl, cacheKey)
}
