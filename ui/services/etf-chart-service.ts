import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import { CHART_COLORS, OTHERS_COLOR } from '../constants/chart-colors'

export interface ChartDataItem {
  label: string
  value: number
  percentage: string
  color: string
  code?: string
}

export interface ChartDataConfig {
  topCount?: number
  threshold?: number
  minThreshold?: number
  colors?: string[]
}

export function buildSectorChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { topCount = 20, minThreshold = 0.5, colors = CHART_COLORS } = config
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

  const aboveThreshold = sortedSectors.filter(s => s.value >= minThreshold)
  const belowThreshold = sortedSectors.filter(s => s.value < minThreshold)
  const mainSectors = aboveThreshold.slice(0, topCount)
  const smallSectors = [...aboveThreshold.slice(topCount), ...belowThreshold]

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

export function buildCountryChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { topCount = 20, minThreshold = 0.2, colors = CHART_COLORS } = config
  const countryTotals = new Map<string, { value: number; code: string }>()

  holdings.forEach(holding => {
    const countryName = holding.holdingCountryName || 'Unknown'
    const countryCode = holding.holdingCountryCode || ''
    const percentage = holding.percentageOfTotal
    const existing = countryTotals.get(countryName) || { value: 0, code: countryCode }
    countryTotals.set(countryName, {
      value: existing.value + percentage,
      code: existing.code || countryCode,
    })
  })

  const sortedCountries = Array.from(countryTotals.entries())
    .sort((a, b) => b[1].value - a[1].value)
    .map(([label, data]) => ({
      label,
      value: data.value,
      percentage: data.value.toFixed(2),
      code: data.code,
    }))

  const aboveThreshold = sortedCountries.filter(c => c.value >= minThreshold)
  const belowThreshold = sortedCountries.filter(c => c.value < minThreshold)
  const mainCountries = aboveThreshold.slice(0, topCount)
  const smallCountries = [...aboveThreshold.slice(topCount), ...belowThreshold]

  const result = [...mainCountries]

  if (smallCountries.length > 0) {
    const othersTotal = smallCountries.reduce((sum, c) => sum + c.value, 0)
    result.push({
      label: 'Others',
      value: othersTotal,
      percentage: othersTotal.toFixed(2),
      code: '',
    })
  }

  return result.map((item, index) => ({
    label: item.label,
    value: item.value,
    percentage: item.percentage,
    color: item.label === 'Others' ? OTHERS_COLOR : colors[index % colors.length],
    code: item.code || undefined,
  }))
}

export function getFilterParam<T>(selected: T[], available: T[]): T[] | undefined {
  if (selected.length === 0 || selected.length === available.length) {
    return undefined
  }
  return selected
}
