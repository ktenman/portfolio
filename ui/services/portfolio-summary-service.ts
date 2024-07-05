import { PortfolioSummary } from '../models/portfolio-summary.ts'

export async function fetchPortfolioSummary(): Promise<PortfolioSummary[]> {
  const response = await fetch('/api/portfolio-summary')
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return (await response.json()) as PortfolioSummary[]
}

export async function fetchLatestPortfolioSummary(): Promise<PortfolioSummary> {
  const response = await fetch('/api/portfolio-summary/latest')
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return (await response.json()) as PortfolioSummary
}
