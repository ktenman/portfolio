import { httpClient } from '../utils/http-client'
import type {
  AnnualWindowsDto,
  PortfolioSummaryDto,
  XirrWindowsDto,
} from '../models/generated/domain-models'
import type { Page } from '../models/page'
import { API_ENDPOINTS } from '../constants'

export const portfolioSummaryService = {
  getHistorical: (page: number, size: number, platforms?: string[]) =>
    httpClient.get<Page<PortfolioSummaryDto>>(API_ENDPOINTS.PORTFOLIO_SUMMARY_HISTORICAL, {
      params: { page, size, ...(platforms?.length ? { platforms } : {}) },
    }),

  getCurrent: (platforms?: string[]) =>
    httpClient.get<PortfolioSummaryDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT, {
      params: platforms?.length ? { platforms } : {},
    }),

  getXirrWindows: (platforms?: string[]) =>
    httpClient.get<XirrWindowsDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_XIRR_WINDOWS, {
      params: platforms?.length ? { platforms } : {},
    }),

  getAnnualWindows: (platforms?: string[]) =>
    httpClient.get<AnnualWindowsDto>(API_ENDPOINTS.PORTFOLIO_SUMMARY_ANNUAL_WINDOWS, {
      params: platforms?.length ? { platforms } : {},
    }),

  recalculate: () =>
    httpClient.post<{ message: string }>(API_ENDPOINTS.PORTFOLIO_SUMMARY_RECALCULATE, undefined, {
      timeout: 60000,
    }),
}
