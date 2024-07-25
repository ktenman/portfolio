import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'

export class SummaryService {
  private apiUrl = '/api/portfolio-summary'

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY)
  async fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
    const response = await fetch(this.apiUrl)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return (await response.json()) as PortfolioSummary[]
  }
}
