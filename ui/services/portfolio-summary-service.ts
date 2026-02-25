import { httpClient } from '../utils/http-client'
import type { PortfolioSummaryDto, ReturnPredictionDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'
import { API_ENDPOINTS } from '../constants'

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number) =>
    httpClient.get<Page<PortfolioSummaryDto>>(API_ENDPOINTS.PORTFOLIO_SUMMARY_HISTORICAL, {
      params: { page, size },
    }),

  getCurrent: () => httpClient.get<PortfolioSummaryDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT),

  recalculate: () =>
    httpClient.post<{ message: string }>(API_ENDPOINTS.PORTFOLIO_SUMMARY_RECALCULATE, undefined, {
      timeout: 60000,
    }),

  getPredictions: (monthlyContribution?: number) =>
    httpClient.get<ReturnPredictionDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_PREDICTIONS, {
      params: monthlyContribution !== undefined ? { monthlyContribution } : undefined,
    }),
}
