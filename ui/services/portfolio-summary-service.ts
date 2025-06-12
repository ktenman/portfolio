import { httpClient } from '../utils/http-client'
import type { PortfolioSummary } from '../models/portfolio-summary'
import type { Page } from '../models/page'

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number) =>
    httpClient
      .get<Page<PortfolioSummary>>(`/portfolio-summary/historical?page=${page}&size=${size}`)
      .then(res => res.data),

  getCurrent: () =>
    httpClient.get<PortfolioSummary>('/portfolio-summary/current').then(res => res.data),

  recalculate: () =>
    httpClient
      .post<{ message: string }>('/portfolio-summary/recalculate', {})
      .then(res => res.data),
}
