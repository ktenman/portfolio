import { ApiClient } from './api-client.ts'
import { CACHE_KEYS } from '../constants/cache-keys.ts'
import { Cacheable } from '../decorators/cacheable.decorator.ts'
import { CalculationResult } from '../models/calculation-result.ts'

export class CalculationService {
  private apiUrl = '/api/calculator'

  @Cacheable(CACHE_KEYS.CALCULATION_RESULT)
  async fetchCalculationResult(): Promise<CalculationResult> {
    return ApiClient.get<CalculationResult>(this.apiUrl)
  }
}
