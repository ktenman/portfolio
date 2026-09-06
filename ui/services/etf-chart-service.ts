import type { EtfHoldingBreakdownDto, InstrumentDto } from '../models/generated/domain-models'
import { DONUT_COLORS } from '../constants/chart-colors'
import {
  compareBreakdown,
  COUNTRY_MIN_PERCENTAGE,
  SECTOR_MIN_PERCENTAGE,
  TOP_COUNT,
} from './diversification-chart-service'

export interface ChartDataItem {
  label: string
  value: number
  percentage: string
  color: string
  code?: string
  benchmark?: number
  ratio?: number
}

export function buildSectorChartData(holdings: EtfHoldingBreakdownDto[]): ChartDataItem[] {
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
    .filter(s => s.value >= SECTOR_MIN_PERCENTAGE)
    .slice(0, TOP_COUNT)
    .map((item, index) => ({
      ...item,
      color: DONUT_COLORS[index % DONUT_COLORS.length],
    }))
}

const sumByIndustry = (holdings: EtfHoldingBreakdownDto[]): Map<string, number> =>
  holdings.reduce((totals, holding) => {
    const label = holding.holdingIndustry ?? 'Unclassified'
    return totals.set(label, (totals.get(label) ?? 0) + holding.percentageOfTotal)
  }, new Map<string, number>())

const toItems = (totals: Map<string, number>) =>
  Array.from(totals.entries()).map(([label, value]) => ({ label, value }))

export function buildIndustryChartData(
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[] = []
): ChartDataItem[] {
  const benchmarkItems = benchmark.length > 0 ? toItems(sumByIndustry(benchmark)) : null
  const rows = compareBreakdown(toItems(sumByIndustry(holdings)), benchmarkItems, {
    topCount: TOP_COUNT,
    minPercentage: SECTOR_MIN_PERCENTAGE,
    withOther: true,
  })
  return rows.map(({ label, value, benchmark: share, ratio }, index) => ({
    label,
    value,
    percentage: value.toFixed(2),
    color: DONUT_COLORS[index % DONUT_COLORS.length],
    ...(share === undefined ? {} : { benchmark: share, ratio }),
  }))
}

export function buildCompanyChartData(holdings: EtfHoldingBreakdownDto[]): ChartDataItem[] {
  return [...holdings]
    .sort((a, b) => b.percentageOfTotal - a.percentageOfTotal)
    .slice(0, TOP_COUNT)
    .map((holding, index) => ({
      label: holding.holdingName,
      value: holding.percentageOfTotal,
      percentage: holding.percentageOfTotal.toFixed(2),
      color: DONUT_COLORS[index % DONUT_COLORS.length],
    }))
}

export function buildCountryChartData(holdings: EtfHoldingBreakdownDto[]): ChartDataItem[] {
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
    .filter(c => c.value >= COUNTRY_MIN_PERCENTAGE)
    .slice(0, TOP_COUNT)
    .map((item, index) => ({
      label: item.label,
      value: item.value,
      percentage: item.percentage,
      color: DONUT_COLORS[index % DONUT_COLORS.length],
      code: item.code || undefined,
    }))
}

export interface WeightedMetrics {
  ter: number | null
  annualReturn: number | null
}

function weightedAverage(
  instruments: InstrumentDto[],
  select: (instrument: InstrumentDto) => number | null
): number | null {
  const totals = instruments.reduce(
    (acc, instrument) => {
      const value = select(instrument)
      if (value === null) return acc
      const weight = instrument.currentValue ?? 0
      return { weight: acc.weight + weight, sum: acc.sum + value * weight }
    },
    { weight: 0, sum: 0 }
  )

  if (totals.weight === 0) return null
  return totals.sum / totals.weight
}

export function calculateWeightedMetrics(
  instruments: InstrumentDto[],
  selectedSymbols: string[]
): WeightedMetrics {
  const selected = new Set(selectedSymbols)
  const funds = instruments.filter(
    instrument => selected.has(instrument.symbol) && (instrument.currentValue ?? 0) > 0
  )

  return {
    ter: weightedAverage(funds, instrument => instrument.ter),
    annualReturn: weightedAverage(funds, instrument => instrument.xirrAnnualReturn),
  }
}

export function getFilterParam<T>(selected: T[], available: T[]): T[] | undefined {
  if (selected.length === 0 || selected.length === available.length) {
    return undefined
  }
  return selected
}
