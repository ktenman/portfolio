import {PortfolioSummary} from '../models/portfolio-summary'

export class PortfolioSummaryService {
  private apiUrl = '/api/portfolio-summary'

  async fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
    const response = await fetch(this.apiUrl)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return (await response.json()) as PortfolioSummary[]
  }
}
