import type { BenchmarkPointDto, PortfolioSummaryDto } from '../models/generated/domain-models'

interface PerformanceSeries {
  portfolioValues: (number | null)[]
  sp500Values: (number | null)[]
  worldValues: (number | null)[]
}

const priceAtOrBefore = (points: BenchmarkPointDto[], date: string): number | null => {
  let price: number | null = null
  for (const current of points) {
    if (current.date > date) break
    price = current.price
  }
  return price
}

const pricesFor = (points: BenchmarkPointDto[], summaries: PortfolioSummaryDto[]) =>
  points.length > 0 ? summaries.map(current => priceAtOrBefore(points, current.date)) : null

const findAnchor = (priceLists: (number | null)[][], length: number): number => {
  if (priceLists.length === 0) return -1
  for (let i = 0; i < length; i++) {
    if (priceLists.every(prices => prices[i] !== null)) return i
  }
  return -1
}

const rebase = (prices: (number | null)[], anchor: number): (number | null)[] => {
  const base = prices[anchor]
  return prices.map((price, i) =>
    i >= anchor && base !== null && price !== null ? (price / base - 1) * 100 : null
  )
}

const compound = (summaries: PortfolioSummaryDto[], anchor: number): (number | null)[] => {
  const values: (number | null)[] = []
  let index = 1
  for (let i = 0; i < summaries.length; i++) {
    if (i < anchor) {
      values.push(null)
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
    values.push((index - 1) * 100)
  }
  return values
}

export function buildPerformanceSeries(
  summaries: PortfolioSummaryDto[],
  sp500: BenchmarkPointDto[],
  world: BenchmarkPointDto[]
): PerformanceSeries {
  const sp500Prices = pricesFor(sp500, summaries)
  const worldPrices = pricesFor(world, summaries)
  const present = [sp500Prices, worldPrices].filter(
    (prices): prices is (number | null)[] => prices !== null
  )
  const anchor = findAnchor(present, summaries.length)
  if (anchor < 0) {
    return {
      portfolioValues: summaries.map(() => null),
      sp500Values: summaries.map(() => null),
      worldValues: summaries.map(() => null),
    }
  }
  return {
    portfolioValues: compound(summaries, anchor),
    sp500Values: sp500Prices ? rebase(sp500Prices, anchor) : summaries.map(() => null),
    worldValues: worldPrices ? rebase(worldPrices, anchor) : summaries.map(() => null),
  }
}
