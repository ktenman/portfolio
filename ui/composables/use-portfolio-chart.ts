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
  sp500Values: (number | null)[]
  worldValues: (number | null)[]
}

export function usePerformanceChart(
  summaries: Ref<PortfolioSummaryDto[]>,
  sp500: Ref<BenchmarkPointDto[]>,
  world: Ref<BenchmarkPointDto[]>
) {
  const performanceChartData = computed<PerformanceChartData | null>(() => {
    if (summaries.value.length === 0) return null
    if (sp500.value.length === 0 && world.value.length === 0) return null

    const chronologicalSummaries = [...summaries.value].sort(
      (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
    )

    const series = buildPerformanceSeries(chronologicalSummaries, sp500.value, world.value)

    return {
      labels: sampleDataPoints(
        chronologicalSummaries.map(item => item.date),
        MAX_CHART_POINTS
      ),
      portfolioValues: sampleDataPoints(series.portfolioValues, MAX_CHART_POINTS),
      sp500Values: sampleDataPoints(series.sp500Values, MAX_CHART_POINTS),
      worldValues: sampleDataPoints(series.worldValues, MAX_CHART_POINTS),
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
