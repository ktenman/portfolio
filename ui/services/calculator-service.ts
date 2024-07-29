import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'

export class CalculatorService {
  private apiUrl = '/api/calculator'

  @Cacheable(CACHE_KEYS.XIRR)
  async getXirr(): Promise<number> {
    const xirr = await ApiClient.get<number>(`${this.apiUrl}`)
    return Number(xirr)
  }
}
