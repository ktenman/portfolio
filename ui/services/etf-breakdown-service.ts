import { httpClient } from '../utils/http-client'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'

export const etfBreakdownService = {
  getBreakdown: (etfSymbols?: string[], platforms?: string[]) => {
    const params: Record<string, string[]> = {}
    if (etfSymbols?.length) params.etfSymbols = etfSymbols
    if (platforms?.length) params.platforms = platforms
    return httpClient
      .get<EtfHoldingBreakdownDto[]>('/etf-breakdown', { params })
      .then(res => res.data)
  },
}
