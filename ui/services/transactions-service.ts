import { httpClient } from '../utils/http-client'
import type {
  TransactionResponseDto,
  TransactionRequestDto,
} from '../models/generated/domain-models'

export const transactionsService = {
  getAll: () => httpClient.get<TransactionResponseDto[]>('/transactions').then(res => res.data),

  create: (data: Partial<TransactionRequestDto>) =>
    httpClient.post<TransactionResponseDto>('/transactions', data).then(res => res.data),

  update: (id: number | string, data: Partial<TransactionRequestDto>) =>
    httpClient.put<TransactionResponseDto>(`/transactions/${id}`, data).then(res => res.data),

  delete: (id: number | string) =>
    httpClient.delete<void>(`/transactions/${id}`).then(() => undefined),
}
