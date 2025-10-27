import { httpClient } from '../utils/http-client'
import { CalculationResult } from '../models/calculation-result'

interface BuildInfo {
  hash: string
  time: string
}

export const utilityService = {
  getCalculationResult: () =>
    httpClient.get<CalculationResult>('/calculator').then(res => res.data),

  getBuildInfo: () => httpClient.get<BuildInfo>('/build-info').then(res => res.data),

  getLogoUrl: (ticker: string): string => `/api/logos/${ticker}`,
}
