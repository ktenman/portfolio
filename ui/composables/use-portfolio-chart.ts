import { computed, Ref } from 'vue'
import { PortfolioSummary } from '../models/portfolio-summary'

interface ChartDataPoint {
  labels: string[]
  totalValues: number[]
  profitValues: number[]
  xirrValues: number[]
  earningsValues: number[]
}

export function usePortfolioChart(summaries: Ref<PortfolioSummary[]>) {
  const modifiedAsap = (data: number[], maxPoints: number): number[] => {
    const step = Math.ceil(data.length / maxPoints)
    return Array.from({ length: maxPoints }, (_, i) => i * step).filter(i => i < data.length)
  }

  const processedChartData = computed<ChartDataPoint | null>(() => {
    if (summaries.value.length === 0) return null

    const labels = summaries.value.map(item => item.date)
    const totalValues = summaries.value.map(item => item.totalValue)
    const profitValues = summaries.value.map(item => item.totalProfit)
    const xirrValues = summaries.value.map(item => item.xirrAnnualReturn * 100)
    const earningsValues = summaries.value.map(item => item.earningsPerMonth)

    const maxPoints = Math.min(window.innerWidth >= 1000 ? 31 : 15, labels.length)
    const indices = modifiedAsap(totalValues, maxPoints)

    return {
      labels: indices.map(i => labels[i]),
      totalValues: indices.map(i => totalValues[i]),
      profitValues: indices.map(i => profitValues[i]),
      xirrValues: indices.map(i => xirrValues[i]),
      earningsValues: indices.map(i => earningsValues[i]),
    }
  })

  return {
    processedChartData,
  }
}
