import { httpClient } from '../utils/http-client'
import type {
  TransactionResponseDto,
  TransactionRequestDto,
} from '../models/generated/domain-models'

export const transactionsService = {
  getAll: (platforms?: string[]) => {
    const params = platforms && platforms.length > 0 ? { platforms: platforms.join(',') } : {}
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
