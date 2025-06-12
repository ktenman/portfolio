import { apiClient } from './api-client'
import { CACHE_KEYS } from '../constants/cache-keys'
import { CalculationResult } from '../models/calculation-result'
import { withCache } from '../utils/cache-utils'

interface BuildInfo {
  hash: string
  time: string
}

export class UtilityService {
  private readonly baseUrl = '/api'

  async getCalculationResult(): Promise<CalculationResult> {
    return withCache(CACHE_KEYS.CALCULATION_RESULT, () =>
      apiClient.get<CalculationResult>(`${this.baseUrl}/calculator`)
    )
  }

  async getBuildInfo(): Promise<BuildInfo> {
    return withCache(CACHE_KEYS.BUILD_INFO, () =>
      apiClient.get<BuildInfo>(`${this.baseUrl}/build-info`)
    )
  }
}

export const utilityService = new UtilityService()
