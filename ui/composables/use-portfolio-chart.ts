import { computed, Ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { BenchmarkPointDto, PortfolioSummaryDto } from '../models/generated/domain-models'
import { buildPerformanceSeries } from '../services/benchmark-comparison'
import { sortSummariesByDateAsc } from '../services/summary-aggregator'
import { STORAGE_KEYS } from '../constants'

export const CHART_MODES = [
  { value: 'value', label: '€' },
  { value: 'sp500', label: '% vs S&P 500' },
  { value: 'world', label: '% vs World' },
] as const

export type ChartMode = (typeof CHART_MODES)[number]['value']

export type BenchmarkKey = Exclude<ChartMode, 'value'>

const BENCHMARK_KEYS: BenchmarkKey[] = ['sp500', 'world']

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

export interface ChartBenchmark {
  key: BenchmarkKey
  label: string
  points: BenchmarkPointDto[]
}

export interface PerformanceBenchmark {
  label: string
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
      labels: sampleDataPoints(
        chronologicalSummaries.map(item => item.date),
        MAX_CHART_POINTS
      ),
      portfolioValues: sampleDataPoints(series.portfolioValues, MAX_CHART_POINTS),
      benchmarks: benchmarks.value.map((benchmark, i) => ({
        label: benchmark.label,
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
    get: () => BENCHMARK_KEYS.filter(key => stored.value.split(',').includes(key)),
    set: value => {
      stored.value = value.join(',')
    },
  })
}
