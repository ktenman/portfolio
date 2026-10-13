import type { PortfolioSummaryDto } from '../models/generated/domain-models'

export function mergeHistoricalWithCurrent(
  historicalSummaries: PortfolioSummaryDto[],
  currentSummary: PortfolioSummaryDto | null | undefined
): PortfolioSummaryDto[] {
  if (!currentSummary) {
    return historicalSummaries
  }

  const result = [...historicalSummaries]
  const existingIndex = result.findIndex(item => item.date === currentSummary.date)

  if (existingIndex >= 0) {
    result[existingIndex] = currentSummary
  } else {
    result.push(currentSummary)
  }

  return result
}

export function mergeSummaryHistory(
  max: PortfolioSummaryDto[],
  recent: PortfolioSummaryDto[],
  current: PortfolioSummaryDto | null | undefined
): PortfolioSummaryDto[] {
  const rows = new Map([...max, ...recent].map(row => [row.date, row]))
  if (current) rows.set(current.date, current)
  return sortSummariesByDateAsc([...rows.values()])
}

export function sortSummariesByDateAsc<T extends { date: string }>(summaries: T[]): T[] {
  return [...summaries].sort((a, b) => a.date.localeCompare(b.date))
}

export function sortSummariesByDateDesc(summaries: PortfolioSummaryDto[]): PortfolioSummaryDto[] {
  return sortSummariesByDateAsc(summaries).reverse()
}

export function findSummaryByDate(
  summaries: PortfolioSummaryDto[],
  date: string
): PortfolioSummaryDto | undefined {
  return summaries.find(summary => summary.date === date)
}

export function percentOfPreviousValue(totalValue: number, change: number): number | null {
  const previousValue = totalValue - change
  return previousValue > 0 ? (change / previousValue) * 100 : null
}

export function flattenPages<T extends { content: PortfolioSummaryDto[] }>(
  pages: T[] | undefined
): PortfolioSummaryDto[] {
  return pages?.flatMap(page => page.content) ?? []
}
