import { computed, Ref } from 'vue'
import { BenchmarkPointDto, PortfolioSummaryDto } from '../models/generated/domain-models'
import { buildPerformanceSeries } from '../services/benchmark-comparison'
import type { ChartMode } from '../components/portfolio/chart-mode-toggle.vue'

interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

const MAX_CHART_POINTS = 60

function sampleDataPoints<T>(array: T[], maxPoints: number): T[] {
  if (array.length <= maxPoints) return array

  const step = (array.length - 1) / (maxPoints - 1)
  return Array.from({ length: maxPoints }, (_, i) => {
    const index = Math.round(i * step)
    return array[index]
  })
}

export function usePortfolioChart(summaries: Ref<PortfolioSummaryDto[]>) {
  const processedChartData = computed<ChartDataPoint | null>(() => {
    if (summaries.value.length === 0) return null

    const chronologicalSummaries = [...summaries.value].sort(
      (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
    )

    const sampledData = sampleDataPoints(chronologicalSummaries, MAX_CHART_POINTS)

    return {
      labels: sampledData.map(item => item.date),
      totalValues: sampledData.map(item => item.totalValue),
      profitValues: sampledData.map(item => item.totalProfit),
      xirrValues: sampledData.map(item => item.xirrAnnualReturn * 100),
      earningsValues: sampledData.map(item => item.earningsPerMonth),
    }
  })

  return {
    processedChartData,
  }
}

export interface PerformanceChartData {
  labels: string[]
  portfolioValues: (number | null)[]
  benchmarkValues: (number | null)[]
  benchmarkLabel: string
}

export function usePerformanceChart(
  summaries: Ref<PortfolioSummaryDto[]>,
  benchmark: Ref<BenchmarkPointDto[]>,
  label: string
) {
  const performanceChartData = computed<PerformanceChartData | null>(() => {
    if (summaries.value.length === 0 || benchmark.value.length === 0) return null

    const chronologicalSummaries = [...summaries.value].sort(
      (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
    )

    const series = buildPerformanceSeries(chronologicalSummaries, benchmark.value)

    return {
      labels: sampleDataPoints(
        chronologicalSummaries.map(item => item.date),
        MAX_CHART_POINTS
      ),
      portfolioValues: sampleDataPoints(series.portfolioValues, MAX_CHART_POINTS),
      benchmarkValues: sampleDataPoints(series.benchmarkValues, MAX_CHART_POINTS),
      benchmarkLabel: label,
    }
  })

  return {
    performanceChartData,
  }
}

export function resolveChartMode(
  mode: ChartMode,
  performanceData: PerformanceChartData | null
): ChartMode {
  if (mode === 'value') return 'value'
  return performanceData ? mode : 'value'
}
