import { ApiClient } from './api-client'
import { CACHE_KEYS } from '../constants/cache-keys'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CalculationResult } from '../models/calculation-result'

export class CalculationService {
  private apiUrl = '/api/calculator'

  @Cacheable(CACHE_KEYS.CALCULATION_RESULT)
  async getResult(): Promise<CalculationResult> {
    return ApiClient.get<CalculationResult>(this.apiUrl)
  }
}
