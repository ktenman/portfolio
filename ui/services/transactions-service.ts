import { httpClient } from '../utils/http-client'
import type {
  TransactionResponseDto,
  TransactionRequestDto,
  TransactionsWithSummaryDto,
} from '../models/generated/domain-models'
import { API_ENDPOINTS } from '../constants'

export const transactionsService = {
  getAll: (platforms?: string[], fromDate?: string, untilDate?: string) => {
    const params: Record<string, string> = {}
    if (platforms && platforms.length > 0) {
      params.platforms = platforms.join(',')
    }
    if (fromDate) {
      params.fromDate = fromDate
    }
    if (untilDate) {
      params.untilDate = untilDate
    }
    return httpClient
      .get<TransactionsWithSummaryDto>(API_ENDPOINTS.TRANSACTIONS, { params })
      .then(res => res.data)
  },

  create: (data: Partial<TransactionRequestDto>) =>
    httpClient.post<TransactionResponseDto>(API_ENDPOINTS.TRANSACTIONS, data).then(res => res.data),

  update: (id: number | string, data: Partial<TransactionRequestDto>) =>
    httpClient
      .put<TransactionResponseDto>(`${API_ENDPOINTS.TRANSACTIONS}/${id}`, data)
      .then(res => res.data),

  delete: (id: number | string) =>
    httpClient.delete<void>(`${API_ENDPOINTS.TRANSACTIONS}/${id}`).then(() => undefined),
}
