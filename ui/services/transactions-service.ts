import { httpClient } from '../utils/http-client'
import type { PortfolioTransaction } from '../models/portfolio-transaction'

export const transactionsService = {
  getAll: () => httpClient.get<PortfolioTransaction[]>('/transactions').then(res => res.data),

  create: (data: Partial<PortfolioTransaction>) =>
    httpClient.post<PortfolioTransaction>('/transactions', data).then(res => res.data),

  update: (id: number | string, data: Partial<PortfolioTransaction>) =>
    httpClient.put<PortfolioTransaction>(`/transactions/${id}`, data).then(res => res.data),

  delete: (id: number | string) =>
    httpClient.delete<void>(`/transactions/${id}`).then(() => undefined),
}
