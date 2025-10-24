import { httpClient } from '../utils/http-client'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'

export const etfBreakdownService = {
  getBreakdown: (etfSymbols?: string[]) => {
    const params = etfSymbols?.length ? { etfSymbols } : {}
    return httpClient
      .get<EtfHoldingBreakdownDto[]>('/etf-breakdown', { params })
      .then(res => res.data)
  },
}
