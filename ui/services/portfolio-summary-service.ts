import {PortfolioSummary} from '../models/portfolio-summary.ts'

export async function fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
  try {
    const response = await fetch('/api/portfolio-summary')
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return (await response.json()) as PortfolioSummary[]
  } catch (error) {
    console.error('Error fetching portfolio summary:', error)
    throw error
  }
}

export async function fetchLatestPortfolioSummary(): Promise<PortfolioSummary> {
  try {
    const response = await fetch('/api/portfolio-summary/latest')
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return (await response.json()) as PortfolioSummary
  } catch (error) {
    console.error('Error fetching latest portfolio summary:', error)
    throw error
  }
}
