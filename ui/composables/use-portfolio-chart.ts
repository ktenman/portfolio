import { computed, Ref } from 'vue'
import { PortfolioSummaryDto } from '../models/generated/domain-models'

interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

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

    const maxPoints = Math.min(window.innerWidth >= 1000 ? 61 : 30, chronologicalSummaries.length)
    const sampledData = sampleDataPoints(chronologicalSummaries, maxPoints)

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
