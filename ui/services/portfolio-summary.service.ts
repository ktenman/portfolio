import { PortfolioSummary } from '../models/portfolio-summary'
import { CACHE_KEYS } from '../constants/cache-keys'
import { apiClient } from './api-client'
import { Page } from '../models/page'
import { withCache } from '../utils/cache-utils'
import { cacheService } from './cache-service'

const API_ENDPOINTS = {
  historical: '/api/portfolio-summary/historical',
  current: '/api/portfolio-summary/current',
  recalculate: '/api/portfolio-summary/recalculate',
} as const

class PortfolioSummaryService {
  async getHistorical(page: number, size: number): Promise<Page<PortfolioSummary>> {
    const result = await apiClient.get<Page<PortfolioSummary>>(
      `${API_ENDPOINTS.historical}?page=${page}&size=${size}`
    )
    if (!result) throw new Error('No data returned from getHistorical')
    return result
  }

  async recalculateAll(): Promise<{ message: string }> {
    const result = await apiClient.post<{ message: string }>(API_ENDPOINTS.recalculate, {})
    cacheService.clearItem(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
    cacheService.clearItem(CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL)
    cacheService.clearItem(CACHE_KEYS.INSTRUMENTS)
    return result || { message: 'Recalculation started' }
  }

  async getCurrent(): Promise<PortfolioSummary> {
    return withCache(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT, () =>
      apiClient.get<PortfolioSummary>(API_ENDPOINTS.current)
    )
  }
}

export const portfolioSummaryService = new PortfolioSummaryService()
