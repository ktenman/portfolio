import { httpClient } from '../utils/http-client'
import { CalculationResult } from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

interface BuildInfo {
  hash: string
  time: string
}

export const utilityService = {
  getCalculationResult: () =>
    httpClient.get<CalculationResult>(API_ENDPOINTS.CALCULATOR).then(res => res.data),

  getBuildInfo: () => httpClient.get<BuildInfo>(API_ENDPOINTS.BUILD_INFO).then(res => res.data),

  getLogoUrl: (uuid: string): string => `/api${API_ENDPOINTS.LOGOS}/${uuid}`,
}
