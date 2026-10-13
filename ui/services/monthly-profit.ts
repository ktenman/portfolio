import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays, addMonths, formatMonthSpan, monthKey } from '../utils/iso-dates'
import { rankText, roundToCents, type ChangeStep } from './daily-profit-calendar'
import { missingMonthName, textsOf, yearTextsOf } from './monthly-profit-texts'
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
  closingText: string
  detail: string
  name: string
}

export interface MissingMonth {
  kind: 'missing'
  key: string
  name: string
}

export type MonthSlot = MonthResult | MissingMonth | null

export interface YearResult {
  year: number
  slots: MonthSlot[]
  change: number | null
  percent: number | null
  note: string
  name: string
}

export interface MonthlyProfit {
  months: MonthResult[]
  years: YearResult[]
  span: string
  upMonths: number
  downMonths: number
  flatMonths: number
  averageMonth: number | null
  excluded: number
  best: MonthResult | null
  worst: MonthResult | null
}

interface ExtremeMonth {
  label: string
  variant: ChangeStep
  month: MonthResult
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

const numberOf = (value: unknown) => (typeof value === 'number' ? value : Number.NaN)

const factorOf = (row: PortfolioSummaryDto, previous: PortfolioSummaryDto): number | null => {
  const difference = row.totalProfit - previous.totalProfit
  const change = roundToCents(difference)
  const base = row.totalValue - change
  if (!Number.isFinite(difference) || !Number.isFinite(base) || !(base > 0)) return null
  const factor = 1 + change / base
  return Number.isFinite(factor) ? factor : null
}

const compound = (factors: Array<number | null>): number | null =>
  factors.reduce<number | null>(
    (product, factor) => (product === null || factor === null ? null : product * factor),
    1
  )

const percentOf = (factor: number | null): number | null =>
  factor === null ? null : (factor - 1) * 100

const indexesByMonth = (rows: PortfolioSummaryDto[]): Map<string, number[]> =>
  rows.slice(1).reduce((groups, row, index) => {
    const key = monthKey(row.date)
    groups.set(key, [...(groups.get(key) ?? []), index + 1])
    return groups
  }, new Map<string, number[]>())

const intervalsOf = (summaries: PortfolioSummaryDto[]): MonthInterval[] => {
  const rows = mergeSummariesByDate(summaries).map(row => ({
    ...row,
    totalProfit: numberOf(row.totalProfit),
    totalValue: numberOf(row.totalValue),
  }))
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

const monthOf = (interval: MonthInterval, today: string, rank: string): MonthResult => {
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

const MONTHS_IN_YEAR = 12

const rankedMonths = (intervals: MonthInterval[], today: string): MonthResult[] => {
  const singles = intervals.filter(interval => interval.isSingleMonth)
  const unit = singles.length === intervals.length ? 'month' : 'single month'
  const changes = singles.map(interval => interval.change)
  return intervals.map(interval =>
    monthOf(interval, today, interval.isSingleMonth ? rankText(interval.change, changes, unit) : '')
  )
}

const extremeOf = (singles: MonthResult[], sign: 1 | -1): MonthResult | null =>
  singles.reduce<MonthResult | null>((extreme, month) => {
    const amount = month.change * sign
    return amount > 0 && amount >= (extreme?.change ?? 0) * sign ? month : extreme
  }, null)

const figuresOf = (intervals: MonthInterval[], months: MonthResult[]) => {
  const singles = months.filter(month => month.isSingleMonth)
  const total = intervals
    .filter(interval => interval.isSingleMonth)
    .reduce((sum, interval) => sum + interval.rawChange, 0)
  const countOf = (side: string) => singles.filter(month => month.step.startsWith(side)).length
  return {
    upMonths: countOf('gain'),
    downMonths: countOf('loss'),
    flatMonths: countOf('flat'),
    averageMonth: singles.length ? roundToCents(total / singles.length) : null,
    excluded: months.length - singles.length,
    best: extremeOf(singles, 1),
    worst: extremeOf(singles, -1),
  }
}

const slotsOf = (year: number, months: MonthResult[]): MonthSlot[] => {
  const first = months[0].key
  const last = months[months.length - 1].key
  return Array.from({ length: MONTHS_IN_YEAR }, (_, index) => {
    const key = addMonths(`${year}-01`, index)
    const month = months.find(candidate => candidate.key === key)
    if (month) return month
    return key > first && key < last ? { kind: 'missing', key, name: missingMonthName(key) } : null
  })
}

const yearOf = (
  year: number,
  intervals: MonthInterval[],
  months: MonthResult[],
  today: string
): YearResult => {
  const own = intervals.filter(interval => interval.key.startsWith(`${year}-`))
  const slots = slotsOf(year, months)
  if (!own.length) {
    return {
      year,
      slots,
      change: null,
      percent: null,
      ...yearTextsOf(year, null, null, null, today),
    }
  }
  const opening = own[0].opening
  const closing = own[own.length - 1].closing
  const isIsolated = opening.date >= `${year - 1}-12-31`
  const change = isIsolated ? roundToCents(closing.totalProfit - opening.totalProfit) : null
  const percent = isIsolated ? percentOf(compound(own.map(interval => interval.factor))) : null
  return {
    year,
    slots,
    change,
    percent,
    ...yearTextsOf(year, { opening, closing, isIsolated }, change, percent, today),
  }
}

const yearsOf = (intervals: MonthInterval[], months: MonthResult[], today: string) => {
  const first = Number(months[0].key.slice(0, 4))
  const last = Number(months[months.length - 1].key.slice(0, 4))
  return Array.from({ length: last - first + 1 }, (_, index) =>
    yearOf(last - index, intervals, months, today)
  )
}

export const buildMonthlyProfit = (
  summaries: PortfolioSummaryDto[],
  today: string
): MonthlyProfit | null => {
  const intervals = intervalsOf(summaries)
  if (!intervals.length) return null
  const months = rankedMonths(intervals, today)
  return {
    months,
    years: yearsOf(intervals, months, today),
    span: formatMonthSpan(months[0].key, months[months.length - 1].key),
    ...figuresOf(intervals, months),
  }
}

export const monthOn = (monthly: MonthlyProfit, key: string): MonthResult | undefined =>
  monthly.months.find(month => month.key === key)

export const moveMonth = (monthly: MonthlyProfit, key: string, step: number): string | null => {
  const { months } = monthly
  const target = addMonths(key, step)
  if (target < months[0].key || target > months[months.length - 1].key) return null
  return monthOn(monthly, target) ? target : moveMonth(monthly, target, step)
}

export const extremeMonths = ({ best, worst }: MonthlyProfit): ExtremeMonth[] =>
  [
    { label: 'Best month', variant: 'gain-4' as const, month: best },
    { label: 'Worst month', variant: 'loss-4' as const, month: worst },
  ].flatMap(({ label, variant, month }) => (month ? [{ label, variant, month }] : []))
