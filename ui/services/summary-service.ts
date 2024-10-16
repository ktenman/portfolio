import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'

export class SummaryService {
  private historicalApiUrl = '/api/portfolio-summary/historical'
  private currentApiUrl = '/api/portfolio-summary/current'

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_HISTORICAL)
  async fetchHistoricalSummary(): Promise<PortfolioSummary[]> {
    return ApiClient.get<PortfolioSummary[]>(this.historicalApiUrl)
  }

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY_CURRENT)
  async fetchCurrentSummary(): Promise<PortfolioSummary> {
    return ApiClient.get<PortfolioSummary>(this.currentApiUrl)
  }

  async fetchAllSummaries(): Promise<PortfolioSummary[]> {
    const [historical, current] = await Promise.all([
      this.fetchHistoricalSummary(),
      this.fetchCurrentSummary(),
    ])
    historical.push(current)
    return historical
  }
}
