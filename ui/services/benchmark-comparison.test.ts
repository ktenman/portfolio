import { describe, it, expect } from 'vitest'
import { buildPerformanceSeries } from './benchmark-comparison'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import type { BenchmarkPointDto } from '../models/generated/domain-models'

const point = (date: string, price: number): BenchmarkPointDto => ({ date, price })

const summary = (date: string, totalValue: number, totalProfit: number) =>
  createPortfolioSummaryDto({ date, totalValue, totalProfit })

describe('buildPerformanceSeries', () => {
  it('should start both series at zero on the anchor date', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0)],
      [[point('2024-01-02', 100)]]
    )

    expect(result).toEqual({ portfolioValues: [0], benchmarkValues: [[0]] })
  })

  it('should rebase benchmark closes against the anchor close', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0), summary('2024-01-03', 1000, 0)],
      [[point('2024-01-02', 100), point('2024-01-03', 110)]]
    )

    expect(result.benchmarkValues[0][1]).toBeCloseTo(10)
  })

  it('should carry the last close forward across a weekend gap', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-05', 1000, 0), summary('2024-01-06', 1000, 0)],
      [[point('2024-01-05', 100)]]
    )

    expect(result.benchmarkValues).toEqual([[0, 0]])
  })

  it('should return nulls before the first benchmark price', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-01', 1000, 0), summary('2024-01-02', 1000, 0)],
      [[point('2024-01-02', 100)]]
    )

    expect(result).toEqual({ portfolioValues: [null, 0], benchmarkValues: [[null, 0]] })
  })

  it('should return only nulls when the benchmark is empty', () => {
    const result = buildPerformanceSeries([summary('2024-01-02', 1000, 0)], [[]])

    expect(result).toEqual({ portfolioValues: [null], benchmarkValues: [[null]] })
  })

  it('should return only nulls when every benchmark price postdates every summary', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0)],
      [[point('2024-01-03', 100)]]
    )

    expect(result).toEqual({ portfolioValues: [null], benchmarkValues: [[null]] })
  })

  it('should anchor every series where all benchmarks have data', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-01', 1000, 0), summary('2024-01-02', 1000, 0)],
      [[point('2024-01-01', 100), point('2024-01-02', 110)], [point('2024-01-02', 50)]]
    )

    expect(result).toEqual({
      portfolioValues: [null, 0],
      benchmarkValues: [
        [null, 0],
        [null, 0],
      ],
    })
  })

  it('should rebase each benchmark against its own anchor close', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 0), summary('2024-01-03', 1000, 0)],
      [
        [point('2024-01-02', 100), point('2024-01-03', 110)],
        [point('2024-01-02', 50), point('2024-01-03', 60)],
      ]
    )

    expect(result.benchmarkValues.map(values => values[1])).toEqual([
      expect.closeTo(10),
      expect.closeTo(20),
    ])
  })

  it('should keep the portfolio flat when a deposit raises value without profit', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 1000, 100), summary('2024-01-03', 2000, 100)],
      [[point('2024-01-02', 100), point('2024-01-03', 100)]]
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
      [[point('2024-01-02', 100), point('2024-01-03', 100), point('2024-01-04', 100)]]
    )

    expect(
      result.portfolioValues.map(value => value !== null && Math.round(value * 100) / 100)
    ).toEqual([0, 10, 21])
  })

  it('should guard against a zero previous total value', () => {
    const result = buildPerformanceSeries(
      [summary('2024-01-02', 0, 0), summary('2024-01-03', 500, 50)],
      [[point('2024-01-02', 100), point('2024-01-03', 100)]]
    )

    expect(result.portfolioValues).toEqual([0, 0])
  })
})
