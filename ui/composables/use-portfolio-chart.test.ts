import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { resolveChartMode, usePerformanceChart, usePortfolioChart } from './use-portfolio-chart'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createPortfolioSummaryDto } from '../tests/fixtures'

const mockSummaries = [
  createPortfolioSummaryDto({
    date: '2023-01-01',
    totalValue: 10000,
    totalProfit: 1000,
    xirrAnnualReturn: 0.12,
    earningsPerDay: 4,
    earningsPerMonth: 120,
  }),
  createPortfolioSummaryDto({
    date: '2023-01-02',
    totalValue: 10500,
    totalProfit: 1500,
    xirrAnnualReturn: 0.15,
    earningsPerDay: 5,
    earningsPerMonth: 150,
  }),
  createPortfolioSummaryDto({
    date: '2023-01-03',
    totalValue: 11000,
    totalProfit: 1500,
    xirrAnnualReturn: 0.14,
    earningsPerDay: 4.67,
    earningsPerMonth: 140,
  }),
]
describe('usePortfolioChart', () => {
  it('should return null when summaries are empty', () => {
    const summaries = ref<PortfolioSummaryDto[]>([])
    const { processedChartData } = usePortfolioChart(summaries)

    expect(processedChartData.value).toBeNull()
  })

  it('should process chart data from summaries', () => {
    const summaries = ref(mockSummaries)
    const { processedChartData } = usePortfolioChart(summaries)

    expect(processedChartData.value).not.toBeNull()
    expect(processedChartData.value?.labels).toEqual(['2023-01-01', '2023-01-02', '2023-01-03'])
    expect(processedChartData.value?.totalValues).toEqual([10000, 10500, 11000])
    expect(processedChartData.value?.profitValues).toEqual([1000, 1500, 1500])
    expect(processedChartData.value?.xirrValues?.[0]).toBe(12)
    expect(processedChartData.value?.xirrValues?.[1]).toBe(15)
    expect(processedChartData.value?.xirrValues?.[2]).toBeCloseTo(14, 5)
    expect(processedChartData.value?.earningsValues).toEqual([120, 150, 140])
  })

  it('should sort summaries chronologically', () => {
    const unsortedSummaries = ref([mockSummaries[2], mockSummaries[0], mockSummaries[1]])
    const { processedChartData } = usePortfolioChart(unsortedSummaries)

    expect(processedChartData.value?.labels).toEqual(['2023-01-01', '2023-01-02', '2023-01-03'])
  })

  it('should handle single data point', () => {
    const summaries = ref([mockSummaries[0]])
    const { processedChartData } = usePortfolioChart(summaries)

    expect(processedChartData.value?.labels).toHaveLength(1)
    expect(processedChartData.value?.labels).toEqual(['2023-01-01'])
    expect(processedChartData.value?.totalValues).toEqual([10000])
  })

  describe('data sampling', () => {
    it('should keep every point when under the limit', () => {
      const manySummaries = Array.from({ length: 50 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-01-${String(i + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(50)
    })

    it('should limit data points to 60 regardless of window width', () => {
      const manySummaries = Array.from({ length: 90 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-01-${String(i + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(60)
    })

    it('should not sample when data points are less than max', () => {
      const summaries = ref(mockSummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(3)
    })

    it('should sample evenly distributed points', () => {
      const manySummaries = Array.from({ length: 61 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-${String(Math.floor(i / 31) + 1).padStart(2, '0')}-${String((i % 31) + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(60)
      expect(processedChartData.value?.labels?.[0]).toBe('2023-01-01')
      expect(processedChartData.value?.labels?.[30]).toBe('2023-02-01')
    })
  })

  it('should react to summaries changes', () => {
    const summaries = ref(mockSummaries.slice(0, 2))
    const { processedChartData } = usePortfolioChart(summaries)

    expect(processedChartData.value?.labels).toHaveLength(2)

    summaries.value = mockSummaries
    expect(processedChartData.value?.labels).toHaveLength(3)
  })

  it('should handle XIRR percentage conversion', () => {
    const summaries = ref([
      createPortfolioSummaryDto({
        ...mockSummaries[0],
        xirrAnnualReturn: 0.1234,
      }),
    ])
    const { processedChartData } = usePortfolioChart(summaries)

    expect(processedChartData.value?.xirrValues).toEqual([12.34])
  })

  it('should handle missing or undefined values gracefully', () => {
    const summariesWithMissing = ref([
      {
        date: '2023-01-01',
        totalValue: 10000,
        realizedProfit: 0,
        unrealizedProfit: 1000,
        totalProfit: 1000,
        xirrAnnualReturn: undefined as unknown as number,
        earningsPerDay: 4,
        earningsPerMonth: null as unknown as number,
        totalProfitChange24h: null,
      },
    ])
    const { processedChartData } = usePortfolioChart(summariesWithMissing)

    expect(processedChartData.value?.xirrValues).toEqual([NaN])
    expect(processedChartData.value?.earningsValues).toEqual([null])
  })
})

describe('usePerformanceChart', () => {
  const buildSummaries = () => [
    createPortfolioSummaryDto({ date: '2024-01-02', totalValue: 1000, totalProfit: 0 }),
    createPortfolioSummaryDto({ date: '2024-01-03', totalValue: 1100, totalProfit: 100 }),
  ]

  const buildBenchmark = () => [
    { date: '2024-01-02', price: 100 },
    { date: '2024-01-03', price: 102 },
  ]

  const dateAt = (offset: number) =>
    new Date(Date.UTC(2024, 0, 1 + offset)).toISOString().slice(0, 10)

  const valueAt = (offset: number) => {
    if (offset < 30) return 1000
    if (offset === 30) return 2000
    return 2100
  }

  const buildDepositHistory = () =>
    Array.from({ length: 61 }, (_, offset) =>
      createPortfolioSummaryDto({
        date: dateAt(offset),
        totalValue: valueAt(offset),
        totalProfit: offset > 30 ? 100 : 0,
      })
    )

  const buildLongBenchmark = () =>
    Array.from({ length: 61 }, (_, offset) => ({ date: dateAt(offset), price: 100 }))

  it('should return null when both benchmark series are empty', () => {
    const { performanceChartData } = usePerformanceChart(ref(buildSummaries()), ref([]), ref([]))

    expect(performanceChartData.value).toBeNull()
  })

  it('should build the chart from the world series alone', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref([]),
      ref(buildBenchmark())
    )

    expect(performanceChartData.value?.worldValues[1]).toBeCloseTo(2)
    expect(performanceChartData.value?.sp500Values).toEqual([null, null])
  })

  it('should return null when the summaries are empty', () => {
    const { performanceChartData } = usePerformanceChart(ref([]), ref(buildBenchmark()), ref([]))

    expect(performanceChartData.value).toBeNull()
  })

  it('should align labels with the sorted summary dates', () => {
    const { performanceChartData } = usePerformanceChart(
      ref([...buildSummaries()].reverse()),
      ref(buildBenchmark()),
      ref([])
    )

    expect(performanceChartData.value?.labels).toEqual(['2024-01-02', '2024-01-03'])
  })

  it('should expose rebased portfolio and benchmark series', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref(buildBenchmark()),
      ref([])
    )

    expect(performanceChartData.value?.portfolioValues[1]).toBeCloseTo(10)
    expect(performanceChartData.value?.sp500Values[1]).toBeCloseTo(2)
  })

  it('should expose both rebased benchmark series at once', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref(buildBenchmark()),
      ref([
        { date: '2024-01-02', price: 80 },
        { date: '2024-01-03', price: 88 },
      ])
    )

    expect(performanceChartData.value?.sp500Values[1]).toBeCloseTo(2)
    expect(performanceChartData.value?.worldValues[1]).toBeCloseTo(10)
  })

  it('should compound the whole history before sampling so deposits dont inflate returns', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildDepositHistory()),
      ref(buildLongBenchmark()),
      ref([])
    )
    const values = performanceChartData.value?.portfolioValues ?? []

    expect(values[values.length - 1]).toBeCloseTo(5)
  })

  it('should sample long histories down to the chart point limit', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildDepositHistory()),
      ref(buildLongBenchmark()),
      ref([])
    )

    expect(performanceChartData.value?.portfolioValues).toHaveLength(60)
  })

  it('should keep long history labels identical to the euro chart', () => {
    const summaries = ref(buildDepositHistory())
    const { performanceChartData } = usePerformanceChart(
      summaries,
      ref(buildLongBenchmark()),
      ref([])
    )
    const { processedChartData } = usePortfolioChart(summaries)

    expect(performanceChartData.value?.labels).toEqual(processedChartData.value?.labels)
  })
})

describe('resolveChartMode', () => {
  it('should fall back to the value mode when the performance series is missing', () => {
    expect(resolveChartMode('performance', null)).toBe('value')
  })

  it('should keep the selected mode when its series is present', () => {
    const data = {
      labels: ['2024-01-02'],
      portfolioValues: [0],
      sp500Values: [0],
      worldValues: [0],
    }

    expect(resolveChartMode('performance', data)).toBe('performance')
  })
})
