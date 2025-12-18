import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import { CHART_COLORS, OTHERS_COLOR } from '../constants/chart-colors'

export interface ChartDataItem {
  label: string
  value: number
  percentage: string
  color: string
}

export interface ChartDataConfig {
  topCount?: number
  threshold?: number
  colors?: string[]
}

export function buildSectorChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { topCount = 20, colors = CHART_COLORS } = config
  const sectorTotals = new Map<string, number>()

  holdings.forEach(holding => {
    const sector = holding.holdingSector || 'Unknown'
    const percentage = holding.percentageOfTotal
    sectorTotals.set(sector, (sectorTotals.get(sector) || 0) + percentage)
  })

  const sortedSectors = Array.from(sectorTotals.entries())
    .sort((a, b) => b[1] - a[1])
    .map(([label, value]) => ({
      label,
      value,
      percentage: value.toFixed(2),
    }))

  const mainSectors = sortedSectors.slice(0, topCount)
  const smallSectors = sortedSectors.slice(topCount)

  const result = [...mainSectors]

  if (smallSectors.length > 0) {
    const othersTotal = smallSectors.reduce((sum, s) => sum + s.value, 0)
    result.push({
      label: 'Others',
      value: othersTotal,
      percentage: othersTotal.toFixed(2),
    })
  }

  return result.map((item, index) => ({
    ...item,
    color: item.label === 'Others' ? OTHERS_COLOR : colors[index % colors.length],
  }))
}

export function buildCompanyChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { threshold = 1.5, colors = CHART_COLORS } = config
  const sortedHoldings = [...holdings].sort((a, b) => b.percentageOfTotal - a.percentageOfTotal)

  const mainHoldings = sortedHoldings.filter(h => h.percentageOfTotal >= threshold)
  const smallHoldings = sortedHoldings.filter(h => h.percentageOfTotal < threshold)

  const result = mainHoldings.map(h => ({
    label: h.holdingName,
    value: h.percentageOfTotal,
    percentage: h.percentageOfTotal.toFixed(2),
  }))

  if (smallHoldings.length > 0) {
    const othersTotal = smallHoldings.reduce((sum, h) => sum + h.percentageOfTotal, 0)
    result.push({
      label: 'Others',
      value: othersTotal,
      percentage: othersTotal.toFixed(2),
    })
  }

  return result.map((item, index) => ({
    ...item,
    color: item.label === 'Others' ? OTHERS_COLOR : colors[index % colors.length],
  }))
}

export function getFilterParam<T>(selected: T[], available: T[]): T[] | undefined {
  if (selected.length === 0 || selected.length === available.length) {
    return undefined
  }
  return selected
}
