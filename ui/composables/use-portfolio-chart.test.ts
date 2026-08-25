import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'
import {
  useBenchmarkSelection,
  usePerformanceChart,
  usePortfolioChart,
  type ChartBenchmark,
} from './use-portfolio-chart'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import { STORAGE_KEYS } from '../constants'
import { CHART_COLORS } from '../constants/chart-colors'

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

  const sp500 = (points: { date: string; price: number }[]): ChartBenchmark[] => [
    { key: 'sp500', label: 'S&P 500', color: CHART_COLORS[1], points },
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

  it('should return null when no benchmark is selected', () => {
    const { performanceChartData } = usePerformanceChart(ref(buildSummaries()), ref([]))

    expect(performanceChartData.value).toBeNull()
  })

  it('should return null when the summaries are empty', () => {
    const { performanceChartData } = usePerformanceChart(ref([]), ref(sp500(buildBenchmark())))

    expect(performanceChartData.value).toBeNull()
  })

  it('should align labels with the sorted summary dates', () => {
    const { performanceChartData } = usePerformanceChart(
      ref([...buildSummaries()].reverse()),
      ref(sp500(buildBenchmark()))
    )

    expect(performanceChartData.value?.labels).toEqual(['2024-01-02', '2024-01-03'])
  })

  it('should expose rebased portfolio and benchmark series', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref(sp500(buildBenchmark()))
    )

    expect(performanceChartData.value?.portfolioValues[1]).toBeCloseTo(10)
    expect(performanceChartData.value?.benchmarks[0].values[1]).toBeCloseTo(2)
  })

  it('should compound the whole history before sampling so deposits dont inflate returns', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildDepositHistory()),
      ref(sp500(buildLongBenchmark()))
    )
    const values = performanceChartData.value?.portfolioValues ?? []

    expect(values[values.length - 1]).toBeCloseTo(5)
  })

  it('should sample long histories down to the chart point limit', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildDepositHistory()),
      ref(sp500(buildLongBenchmark()))
    )

    expect(performanceChartData.value?.portfolioValues).toHaveLength(60)
  })

  it('should keep long history labels identical to the euro chart', () => {
    const summaries = ref(buildDepositHistory())
    const { performanceChartData } = usePerformanceChart(
      summaries,
      ref(sp500(buildLongBenchmark()))
    )
    const { processedChartData } = usePortfolioChart(summaries)

    expect(performanceChartData.value?.labels).toEqual(processedChartData.value?.labels)
  })

  it('should carry each benchmark label into the chart data', () => {
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref<ChartBenchmark[]>([
        { key: 'world', label: 'World', color: CHART_COLORS[3], points: buildBenchmark() },
      ])
    )

    expect(performanceChartData.value?.benchmarks[0].label).toBe('World')
  })

  it('should build a series for every selected benchmark', () => {
    const world = [
      { date: '2024-01-02', price: 50 },
      { date: '2024-01-03', price: 55 },
    ]
    const { performanceChartData } = usePerformanceChart(
      ref(buildSummaries()),
      ref<ChartBenchmark[]>([
        { key: 'sp500', label: 'S&P 500', color: CHART_COLORS[1], points: buildBenchmark() },
        { key: 'world', label: 'World', color: CHART_COLORS[3], points: world },
      ])
    )

    expect(performanceChartData.value?.benchmarks.map(benchmark => benchmark.values[1])).toEqual([
      expect.closeTo(2),
      expect.closeTo(10),
    ])
  })
})

describe('useBenchmarkSelection', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('should default to no benchmarks when nothing is stored', () => {
    expect(useBenchmarkSelection().value).toEqual([])
  })

  it('should keep a stored single benchmark', () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_MODE, 'world')

    expect(useBenchmarkSelection().value).toEqual(['world'])
  })

  it('should keep both stored benchmarks in canonical order', () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_MODE, 'world,sp500')

    expect(useBenchmarkSelection().value).toEqual(['sp500', 'world'])
  })

  it('should normalize a stale euro mode to no benchmarks', () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_MODE, 'value')

    expect(useBenchmarkSelection().value).toEqual([])
  })

  it('should normalize a stale overlay mode to no benchmarks', () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_CHART_MODE, 'performance')

    expect(useBenchmarkSelection().value).toEqual([])
  })

  it('should persist an updated selection', async () => {
    const selection = useBenchmarkSelection()

    selection.value = ['sp500', 'world']
    await nextTick()

    expect(localStorage.getItem(STORAGE_KEYS.SUMMARY_CHART_MODE)).toBe('sp500,world')
  })
})
