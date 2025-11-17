import { httpClient } from '../utils/http-client'
import type {
  TransactionResponseDto,
  TransactionRequestDto,
} from '../models/generated/domain-models'

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
      .get<TransactionResponseDto[]>('/transactions', { params })
      .then(res => res.data)
  },

  create: (data: Partial<TransactionRequestDto>) =>
    httpClient.post<TransactionResponseDto>('/transactions', data).then(res => res.data),

  update: (id: number | string, data: Partial<TransactionRequestDto>) =>
    httpClient.put<TransactionResponseDto>(`/transactions/${id}`, data).then(res => res.data),

  delete: (id: number | string) =>
    httpClient.delete<void>(`/transactions/${id}`).then(() => undefined),
}
