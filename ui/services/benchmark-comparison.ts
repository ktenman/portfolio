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
  const bases = aligned.map(prices => prices[anchor])
  const portfolioValues: (number | null)[] = []
  const benchmarkValues: (number | null)[][] = benchmarks.map(() => [])
  let index = 1
  for (let i = 0; i < summaries.length; i++) {
    if (i < anchor) {
      portfolioValues.push(null)
      benchmarkValues.forEach(values => values.push(null))
      continue
    }
    if (i > anchor) {
      const previous = summaries[i - 1]
      const growth =
        previous.totalValue > 0
          ? (summaries[i].totalProfit - previous.totalProfit) / previous.totalValue
          : 0
      index *= 1 + growth
    }
    portfolioValues.push((index - 1) * 100)
    aligned.forEach((prices, b) => {
      const price = prices[i]
      const base = bases[b]
      benchmarkValues[b].push(base !== null && price !== null ? (price / base - 1) * 100 : null)
    })
  }
  return { portfolioValues, benchmarkValues }
}
