import { httpClient } from '../utils/http-client'
import type { AvailableEtfsDto, EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

export const etfBreakdownService = {
  getBreakdown: (etfSymbols?: string[], platforms?: string[]) => {
    const params: Record<string, string[]> = {}
    if (etfSymbols?.length) params.etfSymbols = etfSymbols
    if (platforms?.length) params.platforms = platforms
    return httpClient.get<EtfHoldingBreakdownDto[]>(API_ENDPOINTS.ETF_BREAKDOWN, { params })
  },
  getAvailableEtfs: (platforms?: string[]) => {
    const params: Record<string, string[]> = {}
    if (platforms?.length) params.platforms = platforms
    return httpClient.get<AvailableEtfsDto>(API_ENDPOINTS.ETF_BREAKDOWN_AVAILABLE, { params })
  },
}
