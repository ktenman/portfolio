import { httpClient } from '../utils/http-client'
import type { TransactionsWithSummaryDto } from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

export const transactionsService = {
  getAll: (platforms?: string[], fromDate?: string, untilDate?: string) =>
    httpClient.get<TransactionsWithSummaryDto>(API_ENDPOINTS.TRANSACTIONS, {
      params: {
        ...(platforms?.length ? { platforms } : {}),
        ...(fromDate ? { fromDate } : {}),
        ...(untilDate ? { untilDate } : {}),
      },
    }),

  getPlatforms: () => httpClient.get<string[]>(API_ENDPOINTS.TRANSACTIONS_PLATFORMS),
}
