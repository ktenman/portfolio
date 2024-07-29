import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiClient } from './api-client.ts'

export class SummaryService {
  private apiUrl = '/api/portfolio-summary'

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY)
  async fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
    return ApiClient.get<PortfolioSummary[]>(this.apiUrl)
  }
}
