import type { EtfHoldingBreakdownDto, InstrumentDto } from '../models/generated/domain-models'
import { DONUT_COLORS } from '../constants/chart-colors'
import {
  compareBreakdown,
  unlessAbsent,
  COUNTRY_MIN_PERCENTAGE,
  INDUSTRY_MIN_PERCENTAGE,
  INDUSTRY_TOP_COUNT,
  SECTOR_MIN_PERCENTAGE,
  TOP_COUNT,
  type BreakdownItem,
  type ComparedRow,
  type CompareOptions,
} from './diversification-chart-service'

export interface ChartDataItem {
  label: string
  value: number
  color: string
  isOther: boolean
  code?: string
  benchmark?: number
  ratio?: number
}

type Items = BreakdownItem[]

const sumBy = (holdings: EtfHoldingBreakdownDto[], keyOf: (h: EtfHoldingBreakdownDto) => string) =>
  Array.from(
    holdings
      .reduce((totals, holding) => {
        const label = keyOf(holding)
        return totals.set(label, (totals.get(label) ?? 0) + holding.percentageOfTotal)
      }, new Map<string, number>())
      .entries()
  ).map(([label, value]) => ({ label, value }))

const sectorItems = (holdings: EtfHoldingBreakdownDto[]): Items =>
  sumBy(holdings, holding => holding.holdingSector || 'Unknown')

const industryItems = (holdings: EtfHoldingBreakdownDto[]): Items =>
  sumBy(holdings, holding => holding.holdingIndustry ?? 'Unclassified')

const companyItems = (holdings: EtfHoldingBreakdownDto[]): Items =>
  holdings.map(holding => ({ label: holding.holdingName, value: holding.percentageOfTotal }))

const countryItems = (holdings: EtfHoldingBreakdownDto[]): Items => {
  const codes = new Map<string, string>()
  holdings.forEach(holding => {
    const name = holding.holdingCountryName || 'Unknown'
    if (!codes.get(name) && holding.holdingCountryCode) codes.set(name, holding.holdingCountryCode)
  })
  return sumBy(holdings, holding => holding.holdingCountryName || 'Unknown').map(item => ({
    ...item,
    code: codes.get(item.label),
  }))
}

const toChartItems = (rows: ComparedRow[]): ChartDataItem[] =>
  rows.map(({ label, value, benchmark: share, ratio, code, isOther }, index) => ({
    label,
    value,
    color: DONUT_COLORS[index % DONUT_COLORS.length],
    isOther,
    ...(code ? { code } : {}),
    ...(share === undefined ? {} : { benchmark: share, ratio }),
  }))

const build = (
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[],
  toItems: (holdings: EtfHoldingBreakdownDto[]) => Items,
  options: CompareOptions
): ComparedRow[] =>
  compareBreakdown(toItems(holdings), benchmark.length > 0 ? toItems(benchmark) : null, options)

export function buildSectorChartData(
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[] = []
): ChartDataItem[] {
  return toChartItems(
    build(holdings, benchmark, sectorItems, {
      topCount: TOP_COUNT,
      minPercentage: SECTOR_MIN_PERCENTAGE,
      withOther: false,
    })
  )
}

export function buildIndustryChartData(
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[] = [],
  topCount: number = INDUSTRY_TOP_COUNT
): ChartDataItem[] {
  return toChartItems(
    build(holdings, benchmark, industryItems, {
      topCount,
      minPercentage: INDUSTRY_MIN_PERCENTAGE,
      withOther: true,
    })
  )
}

export function buildCompanyChartData(
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[] = []
): ChartDataItem[] {
  return toChartItems(
    build(holdings, benchmark, companyItems, {
      topCount: TOP_COUNT,
      minPercentage: 0,
      withOther: false,
    }).map(unlessAbsent)
  )
}

export function buildCountryChartData(
  holdings: EtfHoldingBreakdownDto[],
  benchmark: EtfHoldingBreakdownDto[] = []
): ChartDataItem[] {
  return toChartItems(
    build(holdings, benchmark, countryItems, {
      topCount: TOP_COUNT,
      minPercentage: COUNTRY_MIN_PERCENTAGE,
      withOther: false,
    })
  )
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
