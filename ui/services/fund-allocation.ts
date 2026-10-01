import type { FundReportDto } from '../models/generated/domain-models'
import { paint, type BreakdownRow } from './diversification-chart-service'

export const TULEVA_SYMBOL = 'EE3600001707'

export const CASH = 'Cash'

export type FundStatus = 'held' | 'new' | 'dropped'

export interface FundRow {
  isin: string
  name: string
  weight: number | null
  status: FundStatus
}

export const cashWeight = (report: FundReportDto): number =>
  100 - report.funds.reduce((sum, fund) => sum + fund.weight, 0)

export const compareFunds = (report: FundReportDto, previous?: FundReportDto): FundRow[] => {
  const before = new Set(previous?.funds.map(fund => fund.isin))
  const now = new Set(report.funds.map(fund => fund.isin))
  const held = report.funds.map(fund => ({
    ...fund,
    status: !previous || before.has(fund.isin) ? ('held' as const) : ('new' as const),
  }))
  const dropped = (previous?.funds ?? [])
    .filter(fund => !now.has(fund.isin))
    .map(fund => ({ ...fund, weight: null, status: 'dropped' as const }))
  return [...held, ...dropped]
}

export const buildFundChartData = (report: FundReportDto): BreakdownRow[] =>
  paint(
    [
      ...report.funds.map(fund => ({ label: fund.name, value: fund.weight, isOther: false })),
      { label: CASH, value: cashWeight(report), isOther: false },
    ].filter(row => row.value > 0)
  )

export const formatReportDate = (asOfDate: string): string =>
  asOfDate.split('-').reverse().join('.')
