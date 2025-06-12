import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CacheEvict } from '../decorators/cache-evict.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { apiClient } from './api-client'
import { Page } from '../models/page'

export class PortfolioSummaryService {
  private historicalApiUrl = '/api/portfolio-summary/historical'
  private currentApiUrl = '/api/portfolio-summary/current'

  async getHistorical(page: number, size: number): Promise<Page<PortfolioSummary>> {
    const url = `${this.historicalApiUrl}?page=${page}&size=${size}`
    return await apiClient.get<Page<PortfolioSummary>>(url)
  }

  @CacheEvict([
    CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT,
    CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL,
    CACHE_KEYS.INSTRUMENTS,
  ])
  async recalculateAll(): Promise<any> {
    return apiClient.post<any>('/api/portfolio-summary/recalculate', {})
  }

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
  async getCurrent(): Promise<PortfolioSummary> {
    return apiClient.get<PortfolioSummary>(this.currentApiUrl)
  }
}
