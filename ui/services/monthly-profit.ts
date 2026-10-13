import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays, monthKey } from '../utils/iso-dates'
import { roundToCents, type ChangeStep } from './daily-profit-calendar'
import { textsOf } from './monthly-profit-texts'
import { mergeSummariesByDate } from './summary-aggregator'

export interface MonthResult {
  kind: 'observed'
  key: string
  openingDate: string
  closingDate: string
  closing: PortfolioSummaryDto
  change: number
  percent: number | null
  isSingleMonth: boolean
  step: ChangeStep
  cell: string
  label: string
  coverage: string
  notes: string[]
  rank: string
  detail: string
  name: string
}

export interface MonthInterval {
  key: string
  opening: PortfolioSummaryDto
  closing: PortfolioSummaryDto
  dates: string[]
  rawChange: number
  change: number
  factor: number | null
  isSingleMonth: boolean
  isFromBaseline: boolean
  isLatest: boolean
}

const factorOf = (row: PortfolioSummaryDto, previous: PortfolioSummaryDto): number | null => {
  const difference = row.totalProfit - previous.totalProfit
  const change = roundToCents(difference)
  const base = row.totalValue - change
  if (!Number.isFinite(difference) || !(base > 0)) return null
  const factor = 1 + change / base
  return Number.isFinite(factor) ? factor : null
}

export const compound = (factors: Array<number | null>): number | null =>
  factors.reduce<number | null>(
    (product, factor) => (product === null || factor === null ? null : product * factor),
    1
  )

export const percentOf = (factor: number | null): number | null =>
  factor === null ? null : (factor - 1) * 100

const indexesByMonth = (rows: PortfolioSummaryDto[]): Map<string, number[]> =>
  rows.slice(1).reduce((groups, row, index) => {
    const key = monthKey(row.date)
    groups.set(key, [...(groups.get(key) ?? []), index + 1])
    return groups
  }, new Map<string, number[]>())

export const intervalsOf = (summaries: PortfolioSummaryDto[]): MonthInterval[] => {
  const rows = mergeSummariesByDate(summaries)
  const groups = [...indexesByMonth(rows)]
  return groups.map(([key, indexes], position) => {
    const opening = rows[indexes[0] - 1]
    const closing = rows[indexes[indexes.length - 1]]
    const rawChange = closing.totalProfit - opening.totalProfit
    return {
      key,
      opening,
      closing,
      dates: indexes.map(index => rows[index].date),
      rawChange,
      change: roundToCents(rawChange),
      factor: compound(indexes.map(index => factorOf(rows[index], rows[index - 1]))),
      isSingleMonth: monthKey(addDays(opening.date, 1)) === key,
      isFromBaseline: indexes[0] === 1 && monthKey(opening.date) === key,
      isLatest: position === groups.length - 1,
    }
  })
}

export const monthOf = (interval: MonthInterval, today: string, rank: string): MonthResult => {
  const percent = percentOf(interval.factor)
  return {
    kind: 'observed',
    key: interval.key,
    openingDate: interval.opening.date,
    closingDate: interval.closing.date,
    closing: interval.closing,
    change: interval.change,
    percent,
    isSingleMonth: interval.isSingleMonth,
    rank,
    ...textsOf(interval, percent, today, rank),
  }
}

export const monthsOf = (summaries: PortfolioSummaryDto[], today: string): MonthResult[] =>
  intervalsOf(summaries).map(interval => monthOf(interval, today, ''))
