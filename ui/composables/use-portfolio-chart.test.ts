import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { usePortfolioChart } from './use-portfolio-chart'
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
  beforeEach(() => {
    vi.stubGlobal('window', { innerWidth: 1200 })
  })

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
    it('should limit data points to 31 on desktop', () => {
      vi.stubGlobal('window', { innerWidth: 1200 })

      const manySummaries = Array.from({ length: 50 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-01-${String(i + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(50)
    })

    it('should limit data points to 15 on mobile', () => {
      vi.stubGlobal('window', { innerWidth: 800 })

      const manySummaries = Array.from({ length: 30 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-01-${String(i + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(30)
    })

    it('should not sample when data points are less than max', () => {
      vi.stubGlobal('window', { innerWidth: 1200 })

      const summaries = ref(mockSummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(3)
    })

    it('should sample evenly distributed points', () => {
      vi.stubGlobal('window', { innerWidth: 1200 })

      const manySummaries = Array.from({ length: 61 }, (_, i) => ({
        ...mockSummaries[0],
        date: `2023-${String(Math.floor(i / 31) + 1).padStart(2, '0')}-${String((i % 31) + 1).padStart(2, '0')}`,
        totalValue: 10000 + i * 100,
      }))

      const summaries = ref(manySummaries)
      const { processedChartData } = usePortfolioChart(summaries)

      expect(processedChartData.value?.labels).toHaveLength(61)
      expect(processedChartData.value?.labels?.[0]).toBe('2023-01-01')
      expect(processedChartData.value?.labels?.[30]).toBe('2023-01-31')
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
