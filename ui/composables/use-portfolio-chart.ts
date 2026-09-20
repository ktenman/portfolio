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
  { key: 'vwce', index: BenchmarkIndex.VWCE, label: 'VWCE', color: CHART_COLORS[3] },
] as const

export type BenchmarkKey = (typeof BENCHMARKS)[number]['key']

export interface RangeExtremes {
  low: number
  high: number
}

export interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
  extremes: RangeExtremes | null
}

export type ChartSummary = Pick<
  PortfolioSummaryDto,
  'date' | 'totalValue' | 'totalProfit' | 'xirrAnnualReturn' | 'earningsPerMonth'
>

const MAX_CHART_POINTS = 60
const MIN_EXTREME_POINTS = 3

function findExtremes(values: number[]): RangeExtremes | null {
  if (values.length < MIN_EXTREME_POINTS) return null
  const low = values.indexOf(Math.min(...values))
  const high = values.indexOf(Math.max(...values))
  return values[low] === values[high] ? null : { low, high }
}

function sampleIndices(summaries: ChartSummary[]): number[] {
  if (summaries.length <= MAX_CHART_POINTS) return summaries.map((_, index) => index)

  const step = (summaries.length - 1) / (MAX_CHART_POINTS - 1)
  const evenlySpaced = Array.from({ length: MAX_CHART_POINTS }, (_, i) => Math.round(i * step))
  const extremes = findExtremes(summaries.map(summary => summary.totalValue))
  const kept = extremes ? [extremes.low, extremes.high] : []
  return [...new Set([...evenlySpaced, ...kept])].sort((a, b) => a - b)
}

function pick<T>(array: T[], indices: number[]): T[] {
  return indices.map(index => array[index])
}

export function usePortfolioChart(summaries: Ref<ChartSummary[]>) {
  const processedChartData = computed<ChartDataPoint | null>(() => {
    if (summaries.value.length === 0) return null

    const chronologicalSummaries = sortSummariesByDateAsc(summaries.value)

    const sampledData = pick(chronologicalSummaries, sampleIndices(chronologicalSummaries))
    const totalValues = sampledData.map(item => item.totalValue)

    return {
      labels: sampledData.map(item => item.date),
      totalValues,
      profitValues: sampledData.map(item => item.totalProfit),
      xirrValues: sampledData.map(item => item.xirrAnnualReturn * 100),
      earningsValues: sampledData.map(item => item.earningsPerMonth),
      extremes: findExtremes(totalValues),
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
  key: BenchmarkKey
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
    const indices = sampleIndices(chronologicalSummaries)
    const series = buildPerformanceSeries(
      chronologicalSummaries,
      benchmarks.value.map(benchmark => benchmark.points)
    )

    return {
      labels: pick(chronologicalSummaries, indices).map(item => item.date),
      portfolioValues: pick(series.portfolioValues, indices),
      benchmarks: benchmarks.value.map((benchmark, i) => ({
        key: benchmark.key,
        label: benchmark.label,
        color: benchmark.color,
        values: pick(series.benchmarkValues[i], indices),
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
