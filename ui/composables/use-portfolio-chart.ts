import { computed, Ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import {
  BenchmarkIndex,
  BenchmarkPointDto,
  PortfolioSummaryDto,
} from '../models/generated/domain-models'
import { buildPerformanceSeries } from '../services/benchmark-comparison'
import { sortSummariesByDateAsc } from '../services/summary-aggregator'
import { STORAGE_KEYS } from '../constants'
import { CHART_COLORS } from '../constants/chart-colors'

export const BENCHMARKS = [
  { key: 'sp500', index: BenchmarkIndex.SP500, label: 'S&P 500', color: CHART_COLORS[1] },
  { key: 'world', index: BenchmarkIndex.WORLD, label: 'World', color: CHART_COLORS[3] },
] as const

export type BenchmarkKey = (typeof BENCHMARKS)[number]['key']

export interface ChartDataPoint {
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

    const chronologicalSummaries = sortSummariesByDateAsc(summaries.value)

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

export interface ChartBenchmark {
  key: BenchmarkKey
  label: string
  color: string
  points: BenchmarkPointDto[]
}

export interface PerformanceBenchmark {
  label: string
  color: string
  values: (number | null)[]
}

export interface PerformanceChartData {
  labels: string[]
  portfolioValues: (number | null)[]
  benchmarks: PerformanceBenchmark[]
}

export function usePerformanceChart(
  summaries: Ref<PortfolioSummaryDto[]>,
  benchmarks: Ref<ChartBenchmark[]>
) {
  const performanceChartData = computed<PerformanceChartData | null>(() => {
    if (summaries.value.length === 0 || benchmarks.value.length === 0) return null

    const chronologicalSummaries = sortSummariesByDateAsc(summaries.value)
    const series = buildPerformanceSeries(
      chronologicalSummaries,
      benchmarks.value.map(benchmark => benchmark.points)
    )

    return {
      labels: sampleDataPoints(chronologicalSummaries, MAX_CHART_POINTS).map(item => item.date),
      portfolioValues: sampleDataPoints(series.portfolioValues, MAX_CHART_POINTS),
      benchmarks: benchmarks.value.map((benchmark, i) => ({
        label: benchmark.label,
        color: benchmark.color,
        values: sampleDataPoints(series.benchmarkValues[i], MAX_CHART_POINTS),
      })),
    }
  })

  return {
    performanceChartData,
  }
}

export function useBenchmarkSelection() {
  const stored = useLocalStorage<string>(STORAGE_KEYS.SUMMARY_CHART_MODE, '')

  return computed<BenchmarkKey[]>({
    get: () =>
      BENCHMARKS.filter(benchmark => stored.value.split(',').includes(benchmark.key)).map(
        benchmark => benchmark.key
      ),
    set: value => {
      stored.value = value.join(',')
    },
  })
}
