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

export function sortSummariesByDateAsc(summaries: PortfolioSummaryDto[]): PortfolioSummaryDto[] {
  return [...summaries].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
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

export function flattenPages<T extends { content: PortfolioSummaryDto[] }>(
  pages: T[] | undefined
): PortfolioSummaryDto[] {
  return pages?.flatMap(page => page.content) ?? []
}
