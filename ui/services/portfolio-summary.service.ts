import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { apiClient } from './api-client'
import { Page } from '../models/page'

const API_ENDPOINTS = {
  historical: '/api/portfolio-summary/historical',
  current: '/api/portfolio-summary/current',
  recalculate: '/api/portfolio-summary/recalculate',
} as const

class PortfolioSummaryService {
  async getHistorical(page: number, size: number): Promise<Page<PortfolioSummary>> {
    return apiClient.get<Page<PortfolioSummary>>(
      `${API_ENDPOINTS.historical}?page=${page}&size=${size}`
    )
  }

  @CacheEvict([
    CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT,
    CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL,
    CACHE_KEYS.INSTRUMENTS,
  ])
  async recalculateAll(): Promise<{ message: string }> {
    return apiClient.post(API_ENDPOINTS.recalculate, {})
  }

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
  async getCurrent(): Promise<PortfolioSummary> {
    return apiClient.get<PortfolioSummary>(API_ENDPOINTS.current)
  }
}

export const portfolioSummaryService = new PortfolioSummaryService()
