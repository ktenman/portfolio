import { ApiClient } from './api-client'
import { cacheService } from './cache-service'
import { BaseService } from './base-service'
import { ICrudService } from '../types/service-interfaces'

class GenericCrudService<T extends { id?: number | string }>
  extends BaseService
  implements ICrudService<T>
{
  constructor(
    baseUrl: string,
    protected readonly cacheKey: string
  ) {
    super(baseUrl)
  }

  async getAll(): Promise<T[]> {
    const cached = cacheService.getItem<T[]>(this.cacheKey)
    if (cached) return cached

    const result = await ApiClient.get<T[]>(this.baseUrl)
    cacheService.setItem(this.cacheKey, result)
    return result
  }

  async create(data: Partial<T>): Promise<T> {
    const result = await ApiClient.post<T>(this.baseUrl, data)
    cacheService.clearItem(this.cacheKey)
    return result
  }

  async update(id: number | string, data: Partial<T>): Promise<T> {
    const result = await ApiClient.put<T>(`${this.baseUrl}/${id}`, data)
    cacheService.clearItem(this.cacheKey)
    return result
  }

  async delete(id: number | string): Promise<void> {
    await ApiClient.delete(`${this.baseUrl}/${id}`)
    cacheService.clearItem(this.cacheKey)
  }
}

export function createCrudService<T extends { id?: number | string }>(
  baseUrl: string,
  cacheKey: string
): ICrudService<T> {
  return new GenericCrudService<T>(baseUrl, cacheKey)
}
