import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays } from '../utils/iso-dates'
import type { ChangeStep } from './daily-profit-calendar'
import {
  closingLabel,
  formatMonth,
  monthCoverage,
  monthDescription,
  monthRank,
  monthlySpan,
  yearCoverage,
  yearDescription,
} from './monthly-profit-text'
import { sortSummariesByDateAsc } from './summary-aggregator'

export { formatMonth, formatMonthlyPercent } from './monthly-profit-text'

export interface ProfitMonth {
  key: string
  opening: PortfolioSummaryDto
  closing: PortfolioSummaryDto
  rawChange: number
  change: number
  percent: number | null
  factor: number | null
  step: ChangeStep
  eligible: boolean
  coverage: string[]
  name: string
  rank: string
  closingLabel: string
  isCurrent: boolean
}

export type ProfitMonthSlot =
  | { key: string; kind: 'observed'; month: ProfitMonth }
  | { key: string; kind: 'missing' | 'empty'; name: string }

export interface ProfitYear {
  year: number
  slots: ProfitMonthSlot[]
  change: number | null
  percent: number | null
  coverage: string[]
  name: string
}

export interface MonthlyProfit {
  months: ProfitMonth[]
  years: ProfitYear[]
  span: string
  upMonths: number
  downMonths: number
  flatMonths: number
  averageMonth: number | null
  best: ProfitMonth | null
  worst: ProfitMonth | null
  excludedMonths: number
}

export const MONTHLY_CHANGE_STEPS = [0.3, 1, 2.5, 5]

const GAIN_STEPS = ['flat', 'gain-1', 'gain-2', 'gain-3', 'gain-4'] as const
const LOSS_STEPS = ['flat', 'loss-1', 'loss-2', 'loss-3', 'loss-4'] as const
const roundToCents = (value: number): number => Number(value.toFixed(2)) + 0

export const monthlyStep = (change: number, percent: number | null): ChangeStep => {
  if (percent === null || !Number.isFinite(percent)) {
    if (!Number.isFinite(change) || change === 0) return 'flat'
    return change > 0 ? 'gain-1' : 'loss-1'
  }
  const printed = Number(Math.abs(percent).toFixed(1))
  const reached = MONTHLY_CHANGE_STEPS.filter(step => printed >= step).length
  return (percent < 0 ? LOSS_STEPS : GAIN_STEPS)[reached]
}

const factorOf = (opening: PortfolioSummaryDto, closing: PortfolioSummaryDto): number | null => {
  if (![opening.totalProfit, closing.totalProfit, closing.totalValue].every(Number.isFinite))
    return null
  const change = roundToCents(closing.totalProfit - opening.totalProfit)
  const base = closing.totalValue - change
  if (base <= 0 || !Number.isFinite(base) || !Number.isFinite(change)) return null
  const factor = 1 + change / base
  return Number.isFinite(factor) ? factor : null
}

const compound = (factors: (number | null)[]): number | null => {
  if (factors.some(factor => factor === null)) return null
  const factor = factors.reduce<number>((product, value) => product * (value ?? 1), 1)
  return Number.isFinite(factor) && Number.isFinite(100 * (factor - 1)) ? factor : null
}

const percentageOf = (factor: number | null): number | null =>
  factor === null ? null : 100 * (factor - 1)

interface MonthObservations {
  opening: PortfolioSummaryDto
  rows: PortfolioSummaryDto[]
}

const observationsByMonth = (rows: PortfolioSummaryDto[]): Map<string, MonthObservations> => {
  const months = new Map<string, MonthObservations>()
  rows.slice(1).forEach((row, index) => {
    const key = row.date.slice(0, 7)
    const existing = months.get(key)
    if (existing) existing.rows.push(row)
    else months.set(key, { opening: rows[index], rows: [row] })
  })
  return months
}

const monthOf = (key: string, { opening, rows }: MonthObservations, today: string): ProfitMonth => {
  const closing = rows[rows.length - 1]
  const rawChange = closing.totalProfit - opening.totalProfit
  const change = roundToCents(rawChange)
  const factor = compound(
    rows.map((row, index) => factorOf(index ? rows[index - 1] : opening, row))
  )
  const percent = percentageOf(factor)
  const eligible = addDays(opening.date, 1).slice(0, 7) === key
  return {
    key,
    opening,
    closing,
    rawChange,
    change,
    percent,
    factor,
    eligible,
    step: monthlyStep(change, percent),
    coverage: monthCoverage(opening, rows, today, eligible),
    name: '',
    rank: '',
    closingLabel: closingLabel(closing, today),
    isCurrent: closing.date === today,
  }
}

const rankedMonths = (months: ProfitMonth[]): ProfitMonth[] => {
  const singles = months.filter(month => month.eligible)
  return months.map(month => {
    const ranked = { ...month, rank: monthRank(month, singles, singles.length < months.length) }
    return { ...ranked, name: monthDescription(ranked) }
  })
}

const extremeOf = (months: ProfitMonth[], sign: 1 | -1): ProfitMonth | null =>
  months.reduce<ProfitMonth | null>((extreme, month) => {
    const amount = month.change * sign
    return Number.isFinite(amount) && amount > 0 && amount >= (extreme?.change ?? 0) * sign
      ? month
      : extreme
  }, null)

const figuresOf = (months: ProfitMonth[]) => {
  const singles = months.filter(month => month.eligible)
  const average = singles.reduce((sum, month) => sum + month.rawChange, 0) / singles.length
  const count = (side: string) => singles.filter(month => month.step.startsWith(side)).length
  return {
    upMonths: count('gain'),
    downMonths: count('loss'),
    flatMonths: count('flat'),
    averageMonth: Number.isFinite(average) ? roundToCents(average) : null,
    best: extremeOf(singles, 1),
    worst: extremeOf(singles, -1),
    excludedMonths: months.length - singles.length,
  }
}

const slotsOf = (year: number, months: ProfitMonth[]): ProfitMonthSlot[] => {
  const observed = new Map(months.map(month => [month.key, month]))
  const first = months[0].key
  const last = months[months.length - 1].key
  return Array.from({ length: 12 }, (_, index) => {
    const key = `${year}-${String(index + 1).padStart(2, '0')}`
    const month = observed.get(key)
    if (month) return { key, kind: 'observed', month }
    const kind = key >= first && key <= last ? 'missing' : 'empty'
    return {
      key,
      kind,
      name: kind === 'missing' ? `${formatMonth(key, true)}, no daily summary` : '',
    }
  })
}

const yearOf = (
  year: number,
  months: ProfitMonth[],
  today: string,
  latest: boolean
): ProfitYear => {
  const observed = months.filter(month => Number(month.key.slice(0, 4)) === year)
  const first = observed[0]
  const last = observed[observed.length - 1]
  const isolated = !!first && addDays(first.opening.date, 1).slice(0, 4) === String(year)
  const rawChange =
    first && last ? last.closing.totalProfit - first.opening.totalProfit : Number.NaN
  const change = isolated && Number.isFinite(rawChange) ? roundToCents(rawChange) : null
  const factor = isolated ? compound(observed.map(month => month.factor)) : null
  const percent = percentageOf(factor)
  const coverage = yearCoverage(year, observed, today, latest, isolated)
  const gaps = observed.flatMap(month =>
    month.coverage.filter(note => note.startsWith('No daily summary'))
  )
  return {
    year,
    slots: slotsOf(year, months),
    change,
    percent,
    coverage,
    name: yearDescription(year, change, percent, [...coverage, ...new Set(gaps)], !!first),
  }
}

const yearsOf = (months: ProfitMonth[], today: string): ProfitYear[] => {
  const first = Number(months[0].key.slice(0, 4))
  const last = Number(months[months.length - 1].key.slice(0, 4))
  return Array.from({ length: last - first + 1 }, (_, index) =>
    yearOf(last - index, months, today, index === 0)
  )
}

export const buildMonthlyProfit = (
  summaries: PortfolioSummaryDto[],
  today: string
): MonthlyProfit | null => {
  const rows = sortSummariesByDateAsc([...new Map(summaries.map(row => [row.date, row])).values()])
  if (rows.length < 2) return null
  const months = rankedMonths(
    [...observationsByMonth(rows)].map(([key, observations]) => monthOf(key, observations, today))
  )
  return { months, years: yearsOf(months, today), span: monthlySpan(months), ...figuresOf(months) }
}

export const monthOn = (calendar: MonthlyProfit, key: string): ProfitMonth | undefined =>
  calendar.months.find(month => month.key === key)

const monthPosition = (key: string): number =>
  Number(key.slice(0, 4)) * 12 + Number(key.slice(5, 7)) - 1

export const moveMonth = (calendar: MonthlyProfit, key: string, step: number): string | null => {
  if (!monthOn(calendar, key)) return null
  if (step === 0) return key
  const byPosition = new Map(calendar.months.map(month => [monthPosition(month.key), month.key]))
  const first = monthPosition(calendar.months[0].key)
  const last = monthPosition(calendar.months[calendar.months.length - 1].key)
  for (let target = monthPosition(key) + step; target >= first && target <= last; target += step) {
    const found = byPosition.get(target)
    if (found) return found
  }
  return null
}
