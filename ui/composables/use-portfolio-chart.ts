import { computed, Ref } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'

interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

function sampleDataPoints<T>(array: T[], maxPoints: number): T[] {
  if (array.length <= maxPoints) return array

  const step = Math.ceil(array.length / maxPoints)
  return Array.from({ length: maxPoints }, (_, i) => array[Math.min(i * step, array.length - 1)])
}

export function usePortfolioChart(summaries: Ref<PortfolioSummary[]>) {
  const processedChartData = computed<ChartDataPoint | null>(() => {
    if (summaries.value.length === 0) return null

    const maxPoints = Math.min(window.innerWidth >= 1000 ? 31 : 15, summaries.value.length)
    const sampledData = sampleDataPoints(summaries.value, maxPoints)

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
