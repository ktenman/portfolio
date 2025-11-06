import { httpClient } from '../utils/http-client'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number) =>
    httpClient
      .get<Page<PortfolioSummaryDto>>('/portfolio-summary/historical', {
        params: { page, size },
      })
      .then(res => res.data),

  getCurrent: () =>
    httpClient.get<PortfolioSummaryDto>('/portfolio-summary/current').then(res => res.data),

  recalculate: () =>
    httpClient
      .post<{ message: string }>('/portfolio-summary/recalculate', undefined, {
        timeout: 60000,
      })
      .then(res => res.data),
}
