import { ApiClient } from './api-client'
import { CACHE_KEYS } from '../constants/cache-keys'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CalculationResult } from '../models/calculation-result'
import { BaseService } from './base-service'

interface BuildInfo {
  hash: string
  time: string
}

export class UtilityService extends BaseService {
  constructor() {
    super('/api')
  }

  @Cacheable(CACHE_KEYS.CALCULATION_RESULT)
  async getCalculationResult(): Promise<CalculationResult> {
    return ApiClient.get<CalculationResult>(`${this.baseUrl}/calculator`)
  }

  async getBuildInfo(): Promise<BuildInfo> {
    try {
      return await ApiClient.get<BuildInfo>(`${this.baseUrl}/build-info`)
    } catch (error) {
      console.error('Failed to fetch build info:', error)
      return { hash: 'unknown', time: 'unknown' }
    }
  }
}
