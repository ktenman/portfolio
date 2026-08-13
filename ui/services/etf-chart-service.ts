import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import { DONUT_COLORS } from '../constants/chart-colors'

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
  const { topCount = 15, minThreshold = 0.5, colors = DONUT_COLORS } = config
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

  return sortedSectors
    .filter(s => s.value >= minThreshold)
    .slice(0, topCount)
    .map((item, index) => ({
      ...item,
      color: colors[index % colors.length],
    }))
}

export function buildCompanyChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { topCount = 15, colors = DONUT_COLORS } = config

  return [...holdings]
    .sort((a, b) => b.percentageOfTotal - a.percentageOfTotal)
    .slice(0, topCount)
    .map((holding, index) => ({
      label: holding.holdingName,
      value: holding.percentageOfTotal,
      percentage: holding.percentageOfTotal.toFixed(2),
      color: colors[index % colors.length],
    }))
}

export function buildCountryChartData(
  holdings: EtfHoldingBreakdownDto[],
  config: ChartDataConfig = {}
): ChartDataItem[] {
  const { topCount = 15, minThreshold = 0.2, colors = DONUT_COLORS } = config
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

  return sortedCountries
    .filter(c => c.value >= minThreshold)
    .slice(0, topCount)
    .map((item, index) => ({
      label: item.label,
      value: item.value,
      percentage: item.percentage,
      color: colors[index % colors.length],
      code: item.code || undefined,
    }))
}

export function getFilterParam<T>(selected: T[], available: T[]): T[] | undefined {
  if (selected.length === 0 || selected.length === available.length) {
    return undefined
  }
  return selected
}
