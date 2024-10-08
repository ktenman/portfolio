import { Instrument } from '../models/instrument'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CachePut } from '../decorators/cache-put.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'

export class InstrumentService {
  private apiUrl = '/api/instruments'

  @Cacheable(CACHE_KEYS.INSTRUMENTS)
  async getAllInstruments(): Promise<Instrument[]> {
    return ApiClient.get<Instrument[]>(this.apiUrl)
  }

  @CachePut(CACHE_KEYS.INSTRUMENTS)
  async saveInstrument(instrument: Instrument): Promise<Instrument> {
    return ApiClient.post<Instrument>(this.apiUrl, instrument)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async updateInstrument(id: number, instrument: Instrument): Promise<Instrument> {
    return ApiClient.put<Instrument>(`${this.apiUrl}/${id}`, instrument)
  }

  @CacheEvict(CACHE_KEYS.INSTRUMENTS)
  async deleteInstrument(id: number): Promise<void> {
    await ApiClient.delete(`${this.apiUrl}/${id}`)
  }
}
