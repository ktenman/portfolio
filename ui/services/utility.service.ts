import { apiClient } from './api-client'
import { CACHE_KEYS } from '../constants/cache-keys'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CalculationResult } from '../models/calculation-result'

interface BuildInfo {
  hash: string
  time: string
}

export class UtilityService {
  private readonly baseUrl = '/api'

  @Cacheable(CACHE_KEYS.CALCULATION_RESULT)
  async getCalculationResult(): Promise<CalculationResult> {
    return apiClient.get<CalculationResult>(`${this.baseUrl}/calculator`)
  }

  @Cacheable(CACHE_KEYS.BUILD_INFO)
  async getBuildInfo(): Promise<BuildInfo> {
    return apiClient.get<BuildInfo>(`${this.baseUrl}/build-info`)
  }
}

export const utilityService = new UtilityService()
