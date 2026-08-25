import { describe, it, expect } from 'vitest'
import { buildPerformanceSeries } from './benchmark-comparison'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import type { BenchmarkPointDto } from '../models/generated/domain-models'

const point = (date: string, price: number): BenchmarkPointDto => ({ date, price })

const summary = (date: string, totalValue: number, totalProfit: number) =>
  createPortfolioSummaryDto({ date, totalValue, totalProfit })

describe('buildPerformanceSeries', () => {
  it('should start all three series at zero on the anchor date', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0)],
      [point('2024-01-02', 100)],
      [point('2024-01-02', 90)]
    )

    expect(result).toEqual({ portfolioValues: [0], sp500Values: [0], worldValues: [0] })
  })

  it('should rebase each benchmark against its own anchor close', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0), summary('2024-01-03', 1000, 0)],
      [point('2024-01-02', 100), point('2024-01-03', 110)],
      [point('2024-01-02', 80), point('2024-01-03', 84)]
    )

    expect(result.sp500Values[1]).toBeCloseTo(10)
    expect(result.worldValues[1]).toBeCloseTo(5)
  })

  it('should carry the last close forward across a weekend gap', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-05', 1000, 0), summary('2024-01-06', 1000, 0)],
      [point('2024-01-05', 100)],
      [point('2024-01-05', 90)]
    )

    expect(result.sp500Values).toEqual([0, 0])
    expect(result.worldValues).toEqual([0, 0])
  })

  it('should anchor all three lines where the later benchmark begins', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-01', 1000, 0), summary('2024-01-02', 1000, 0)],
      [point('2024-01-01', 100), point('2024-01-02', 110)],
      [point('2024-01-02', 90)]
    )

    expect(result).toEqual({
      portfolioValues: [null, 0],
      sp500Values: [null, 0],
      worldValues: [null, 0],
    })
  })

  it('should keep an empty world series from constraining the anchor', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0), summary('2024-01-03', 1000, 0)],
      [point('2024-01-02', 100), point('2024-01-03', 110)],
      []
    )

    expect(result.sp500Values[1]).toBeCloseTo(10)
    expect(result.worldValues).toEqual([null, null])
  })

  it('should rebase the world series when the sp500 series is empty', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0), summary('2024-01-03', 1000, 0)],
      [],
      [point('2024-01-02', 90), point('2024-01-03', 99)]
    )

    expect(result.sp500Values).toEqual([null, null])
    expect(result.worldValues[1]).toBeCloseTo(10)
  })

  it('should return only nulls when both benchmarks are empty', () => {
    const result = buildPerformanceSeries([summary('2024-01-02', 1000, 0)], [], [])

    expect(result).toEqual({ portfolioValues: [null], sp500Values: [null], worldValues: [null] })
  })

  it('should keep the portfolio flat when a deposit raises value without profit', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 100), summary('2024-01-03', 2000, 100)],
      [point('2024-01-02', 100), point('2024-01-03', 100)],
      [point('2024-01-02', 90), point('2024-01-03', 90)]
    )

    expect(result.portfolioValues).toEqual([0, 0])
  })

  it('should chain profit growth against the previous total value', () => {
    const result = buildPerformanceSeries(
      [
        summary('2024-01-02', 1000, 0),
        summary('2024-01-03', 1100, 100),
        summary('2024-01-04', 1210, 210),
      ],
      [point('2024-01-02', 100), point('2024-01-03', 100), point('2024-01-04', 100)],
      []
    )

    expect(
      result.portfolioValues.map(value => value !== null && Math.round(value * 100) / 100)
    ).toEqual([0, 10, 21])
  })

  it('should guard against a zero previous total value', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 0, 0), summary('2024-01-03', 500, 50)],
      [point('2024-01-02', 100), point('2024-01-03', 100)],
      []
    )

    expect(result.portfolioValues).toEqual([0, 0])
  })
})
