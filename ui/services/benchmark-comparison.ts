import type { BenchmarkPointDto, PortfolioSummaryDto } from '../models/generated/domain-models'

interface PerformanceSeries {
  portfolioValues: (number | null)[]
  benchmarkValues: (number | null)[][]
}

const alignPrices = (
  summaries: PortfolioSummaryDto[],
  points: BenchmarkPointDto[]
): (number | null)[] => {
  const prices: (number | null)[] = []
  let cursor = 0
  let price: number | null = null
  for (const current of summaries) {
    while (cursor < points.length && points[cursor].date <= current.date) {
      price = points[cursor].price
      cursor++
    }
    prices.push(price)
  }
  return prices
}

export function buildPerformanceSeries(
  summaries: PortfolioSummaryDto[],
  benchmarks: BenchmarkPointDto[][]
): PerformanceSeries {
  const aligned = benchmarks.map(points => alignPrices(summaries, points))
  const anchor = summaries.findIndex((_, i) => aligned.every(prices => prices[i] !== null))
  if (anchor < 0) {
    return {
      portfolioValues: summaries.map(() => null),
      benchmarkValues: benchmarks.map(() => summaries.map(() => null)),
    }
  }
  let index = 1
  const portfolioValues = summaries.map((summary, i) => {
    if (i < anchor) return null
    if (i > anchor) {
      const previous = summaries[i - 1]
      const earned = summary.totalProfit - previous.totalProfit
      const deposited = summary.totalValue - previous.totalValue - earned
      const invested = previous.totalValue + Math.max(deposited, 0)
      if (invested > 0) index *= 1 + earned / invested
    }
    return (index - 1) * 100
  })
  const benchmarkValues = aligned.map(prices => {
    const base = prices[anchor]
    return prices.map((price, i) =>
      i < anchor || base === null || price === null ? null : (price / base - 1) * 100
    )
  })
  return { portfolioValues, benchmarkValues }
}
