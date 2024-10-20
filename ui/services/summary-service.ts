import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'
import { Page } from "../models/page.ts"

export class SummaryService {
  private historicalApiUrl = '/api/portfolio-summary/historical'
  private currentApiUrl = '/api/portfolio-summary/current'

  async fetchHistoricalSummary(page: number, size: number): Promise<Page<PortfolioSummary>> {
    const url = `${this.historicalApiUrl}?page=${page}&size=${size}`
    try {
      return await ApiClient.get<Page<PortfolioSummary>>(url)
    } catch (error) {
      console.error('Error fetching historical summary:', error)
      throw error
    }
  }

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
  async fetchCurrentSummary(): Promise<PortfolioSummary> {
    try {
      return await ApiClient.get<PortfolioSummary>(this.currentApiUrl)
    } catch (error) {
      console.error('Error fetching current summary:', error)
      throw error
    }
  }
}
