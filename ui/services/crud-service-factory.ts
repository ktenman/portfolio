import { apiClient } from './api-client'
import { cacheService } from './cache-service'
import { ICrudService } from '../types/service-interfaces'

class GenericCrudService<T extends { id?: number | string }> implements ICrudService<T> {
  constructor(
    private readonly baseUrl: string,
    private readonly cacheKey: string
  ) {}

  async getAll(): Promise<T[]> {
    const cached = cacheService.getItem<T[]>(this.cacheKey)
    if (cached) return cached

    const result = await apiClient.get<T[]>(this.baseUrl)
    cacheService.setItem(this.cacheKey, result)
    return result
  }

  async create(data: Partial<T>): Promise<T> {
    const result = await apiClient.post<T>(this.baseUrl, data)
    cacheService.clearItem(this.cacheKey)
    return result
  }

  async update(id: number | string, data: Partial<T>): Promise<T> {
    const result = await apiClient.put<T>(`${this.baseUrl}/${id}`, data)
    cacheService.clearItem(this.cacheKey)
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
