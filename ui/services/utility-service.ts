import { httpClient } from '../utils/http-client'
import { CalculationResult } from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

interface BuildInfo {
  hash: string
  time: string
}

export const utilityService = {
  getCalculationResult: () => httpClient.get<CalculationResult>(API_ENDPOINTS.CALCULATOR),

  getBuildInfo: () => httpClient.get<BuildInfo>(API_ENDPOINTS.BUILD_INFO),

  getLogoUrl: (uuid: string): string => `/api${API_ENDPOINTS.LOGOS}/${uuid}`,
}
