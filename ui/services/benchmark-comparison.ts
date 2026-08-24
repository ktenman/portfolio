import type { BenchmarkPointDto, PortfolioSummaryDto } from '../models/generated/domain-models'

interface PerformanceSeries {
  portfolioValues: (number | null)[]
  benchmarkValues: (number | null)[]
}

const priceAtOrBefore = (points: BenchmarkPointDto[], date: string): number | null => {
  let price: number | null = null
  for (const current of points) {
    if (current.date > date) break
    price = current.price
  }
  return price
}

export function buildPerformanceSeries(
  summaries: PortfolioSummaryDto[],
  benchmark: BenchmarkPointDto[]
): PerformanceSeries {
  const prices = summaries.map(current => priceAtOrBefore(benchmark, current.date))
  const anchor = prices.findIndex(price => price !== null)
  if (anchor < 0) {
    return {
      portfolioValues: summaries.map(() => null),
      benchmarkValues: summaries.map(() => null),
    }
  }
  const base = prices[anchor]
  const portfolioValues: (number | null)[] = []
  const benchmarkValues: (number | null)[] = []
  let index = 1
  for (let i = 0; i < summaries.length; i++) {
    if (i < anchor) {
      portfolioValues.push(null)
      benchmarkValues.push(null)
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
    const price = prices[i]
    portfolioValues.push((index - 1) * 100)
    benchmarkValues.push(base !== null && price !== null ? (price / base - 1) * 100 : null)
  }
  return { portfolioValues, benchmarkValues }
}
