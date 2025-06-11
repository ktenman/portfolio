import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client'
import { Page } from '../models/page'

export class PortfolioSummaryService {
  private historicalApiUrl = '/api/portfolio-summary/historical'
  private currentApiUrl = '/api/portfolio-summary/current'

  async getHistorical(page: number, size: number): Promise<Page<PortfolioSummary>> {
    const url = `${this.historicalApiUrl}?page=${page}&size=${size}`
    return await ApiClient.get<Page<PortfolioSummary>>(url)
  }

  async recalculateAll(): Promise<any> {
    return ApiClient.post<any>('/api/portfolio-summary/recalculate', {})
  }

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
  async getCurrent(): Promise<PortfolioSummary> {
    return ApiClient.get<PortfolioSummary>(this.currentApiUrl)
  }
}
