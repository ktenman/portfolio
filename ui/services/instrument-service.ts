import { Instrument } from '../models/instrument'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client'
import { BaseService } from './base-service'

export class InstrumentService extends BaseService {
  constructor() {
    super('/api/instruments')
  }

  @Cacheable(CACHE_KEYS.INSTRUMENTS)
  async getAll(): Promise<Instrument[]> {
    return ApiClient.get<Instrument[]>(this.baseUrl)
  }

  @CachePut(CACHE_KEYS.INSTRUMENTS)
  async create(instrument: Partial<Instrument>): Promise<Instrument> {
    return ApiClient.post<Instrument>(this.baseUrl, instrument)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async update(id: string | number, instrument: Partial<Instrument>): Promise<Instrument> {
    return ApiClient.put<Instrument>(`${this.baseUrl}/${id}`, instrument)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async delete(id: string | number): Promise<void> {
    await ApiClient.delete(`${this.baseUrl}/${id}`)
  }
}
