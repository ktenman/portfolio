import { httpClient } from '../utils/http-client'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number) =>
    httpClient
      .get<Page<PortfolioSummaryDto>>(`/portfolio-summary/historical?page=${page}&size=${size}`)
      .then(res => res.data),

  getCurrent: () =>
    httpClient.get<PortfolioSummaryDto>('/portfolio-summary/current').then(res => res.data),

  recalculate: () =>
    httpClient
      .post<{ message: string }>('/portfolio-summary/recalculate', {})
      .then(res => res.data),
}
