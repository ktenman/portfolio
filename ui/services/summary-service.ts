import { PortfolioSummary } from '../models/portfolio-summary'
import { Cacheable } from '../decorators/cacheable.decorator'
import { CACHE_KEYS } from '../constants/cache-keys'
import { ApiError } from '../models/api-error'

export class SummaryService {
  private apiUrl = '/api/portfolio-summary'

  @Cacheable(CACHE_KEYS.PORTFOLIO_SUMMARY)
  async fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
    return this.makeRequest(this.apiUrl)
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<any> {
    const response = await fetch(url, {
      ...options,
      redirect: 'manual', // This prevents automatic redirect following
    })

    if (response.type === 'opaqueredirect' || response.status === 302) {
      // Handle redirect by forcing a full page reload
      window.location.href = response.headers.get('Location') || '/login'
      // Force reload to ensure Caddy handles the redirect
      window.location.reload()
      throw new Error('Redirecting and reloading page')
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new ApiError(
        response.status,
        errorData.message || `HTTP error! status: ${response.status}`,
        errorData.debugMessage || 'No debug message provided',
        errorData.validationErrors || {}
      )
    }

    return response.json()
  }
}
