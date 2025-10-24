import { httpClient } from '../utils/http-client'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'

export const etfBreakdownService = {
  getBreakdown: () =>
    httpClient.get<EtfHoldingBreakdownDto[]>('/etf-breakdown').then(res => res.data),
}
