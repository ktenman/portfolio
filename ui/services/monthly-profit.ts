import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import {
  addDays,
  addMonths,
  daysBetween,
  formatDayMonth,
  formatFullMonth,
  formatMonth,
  formatMonthSpan,
  lastDayOfMonth,
} from '../utils/iso-dates'
import {
  closingTexts,
  extremeOf,
  GAIN_STEPS,
  gapText,
  LOSS_STEPS,
  rankText,
  roundToCents,
  SEPARATOR,
  type ChangeStep,
} from './daily-profit-calendar'
import { mergeSummariesByDate } from './summary-aggregator'

export const MONTH_STEPS = [0.3, 1, 2.5, 5]
export const MONTH_FLAT_HINT = `under ±${MONTH_STEPS[0]}%`
export const NOT_ENOUGH_HISTORY = 'Not enough daily summary history to calculate monthly changes.'

export interface ObservedMonth {
  kind: 'observed'
  key: string
  row: PortfolioSummaryDto
  opening: PortfolioSummaryDto
  change: number
  percent: number | null
  factor: number | null
  step: ChangeStep
  isSingle: boolean
  isOpen: boolean
  label: string
  title: string
  coverage: string
  name: string
  closing: string
  detail: string
  note: string
}

export interface MonthlyProfit {
  months: ObservedMonth[]
  span: string
  upMonths: number
  downMonths: number
  flatMonths: number
  excluded: string
  averageMonth: number | null
  best: ObservedMonth | null
  worst: ObservedMonth | null
}

interface ExtremeMonth {
  label: string
  variant: ChangeStep
  month: ObservedMonth
}

interface MonthRows {
  key: string
  opening: PortfolioSummaryDto
  rows: PortfolioSummaryDto[]
}

type MonthFigures = Omit<ObservedMonth, 'name' | 'closing' | 'detail'>

const keyOf = (row: PortfolioSummaryDto): string => row.date.slice(0, 7)

const monthNumber = (date: string): number =>
  Number(date.slice(0, 4)) * 12 + Number(date.slice(5, 7))

const monthGroups = (rows: PortfolioSummaryDto[]): MonthRows[] => {
  const later = rows.slice(1)
  const starts = later.flatMap((row, index) =>
    index > 0 && keyOf(row) === keyOf(later[index - 1]) ? [] : [index]
  )
  return starts.map((start, index) => ({
    key: keyOf(later[start]),
    opening: rows[start],
    rows: later.slice(start, starts[index + 1]),
  }))
}

const factorOf = (previous: PortfolioSummaryDto, row: PortfolioSummaryDto): number | null => {
  const diff = row.totalProfit - previous.totalProfit
  if (!Number.isFinite(diff) || !Number.isFinite(row.totalValue)) return null
  const change = roundToCents(diff)
  const base = row.totalValue - change
  return Number.isFinite(base) && base > 0 ? 1 + change / base : null
}

const compoundFactor = (chain: PortfolioSummaryDto[]): number | null =>
  chain.slice(1).reduce<number | null>((product, row, index) => {
    const factor = factorOf(chain[index], row)
    return product === null || factor === null ? null : product * factor
  }, 1)

const sideOf = (value: number) => (value > 0 ? GAIN_STEPS : LOSS_STEPS)

export const monthStepOf = (change: number, percent: number | null): ChangeStep => {
  if (percent === null) return change === 0 ? 'flat' : sideOf(change)[1]
  const rounded = Number(Math.abs(percent).toFixed(1))
  const reached = MONTH_STEPS.filter(step => rounded >= step).length
  return sideOf(percent)[reached]
}

const openingText = (date: string, key: string): string =>
  date.startsWith(key.slice(0, 4))
    ? formatDayMonth(date)
    : `${formatDayMonth(date)} ${date.slice(0, 4)}`

const coverageOf = (key: string, opening: string, closing: string, isOpen: boolean): string => {
  const from =
    opening === lastDayOfMonth(addMonths(key, -1)) ? '' : `from ${openingText(opening, key)}`
  const to = closing === lastDayOfMonth(key) && !isOpen ? '' : `to ${formatDayMonth(closing)}`
  return [from, to].filter(Boolean).join(' ')
}

const coversNote = ({ key, opening, rows }: MonthRows): string => {
  const start = addDays(opening.date, 1)
  const count = monthNumber(key) - monthNumber(start) + 1
  return `Covers ${count} months, no daily summary for ${gapText(start, addDays(rows[0].date, -1))}`
}

const gapsNote = (rows: PortfolioSummaryDto[]): string => {
  const runs = rows.slice(1).flatMap(({ date }, index) => {
    const previous = rows[index].date
    return daysBetween(previous, date) > 1 ? [gapText(addDays(previous, 1), addDays(date, -1))] : []
  })
  return runs.length ? `No daily summary for ${runs.join(', ')}` : ''
}

const noteOf = (group: MonthRows, isSingle: boolean, isLatest: boolean, percent: number | null) => {
  const { key, opening, rows } = group
  const closing = rows[rows.length - 1].date
  const isEarlyClose = !isLatest && closing !== lastDayOfMonth(key)
  return [
    isSingle ? '' : coversNote(group),
    gapsNote(isSingle ? [opening, ...rows] : rows),
    isEarlyClose ? `No daily summary after ${formatDayMonth(closing)}` : '',
    percent === null ? 'Percentage unavailable' : '',
  ]
    .filter(Boolean)
    .join(SEPARATOR)
}

const figuresOf = (group: MonthRows, isLatest: boolean, today: string): MonthFigures => {
  const { key, opening, rows } = group
  const row = rows[rows.length - 1]
  const factor = compoundFactor([opening, ...rows])
  const percent = factor === null ? null : 100 * (factor - 1)
  const change = roundToCents(row.totalProfit - opening.totalProfit)
  const isSingle = opening.date >= lastDayOfMonth(addMonths(key, -1))
  const isOpen = row.date === today
  return {
    kind: 'observed',
    key,
    row,
    opening,
    change,
    percent,
    factor,
    step: monthStepOf(change, percent),
    isSingle,
    isOpen,
    label: formatMonth(key),
    title: formatFullMonth(key),
    coverage: coverageOf(key, opening.date, row.date, isOpen),
    note: noteOf(group, isSingle, isLatest, percent),
  }
}

const observedMonth = (figures: MonthFigures, rank: string): ObservedMonth => {
  const { title, coverage, change, percent, row, isOpen } = figures
  const name = [
    title,
    coverage,
    formatCurrencyChange(change),
    percent === null ? 'percentage unavailable' : formatPercentChange(percent, 1),
    rank && `${rank}, ranked by euro profit`,
  ]
  return { ...figures, name: name.filter(Boolean).join(', '), ...closingTexts(row, rank, isOpen) }
}

const rankedMonths = (figures: MonthFigures[]): ObservedMonth[] => {
  const changes = figures.filter(month => month.isSingle).map(month => month.change)
  const unit = changes.length === figures.length ? 'month' : 'single month'
  return figures.map(month =>
    observedMonth(month, month.isSingle ? rankText(month.change, changes, unit) : '')
  )
}

const countOf = (months: ObservedMonth[], side: 'gain' | 'loss' | 'flat'): number =>
  months.filter(month => month.step.startsWith(side)).length

const excludedText = (count: number): string =>
  count
    ? `${count} ${count === 1 ? 'month' : 'months'} not counted, covering more than one month`
    : ''

const averageOf = (singles: ObservedMonth[]): number | null => {
  if (!singles.length) return null
  const total = singles.reduce(
    (sum, { row, opening }) => sum + row.totalProfit - opening.totalProfit,
    0
  )
  return roundToCents(total / singles.length)
}

const statisticsOf = (months: ObservedMonth[]) => {
  const singles = months.filter(month => month.isSingle)
  return {
    upMonths: countOf(singles, 'gain'),
    downMonths: countOf(singles, 'loss'),
    flatMonths: countOf(singles, 'flat'),
    excluded: excludedText(months.length - singles.length),
    averageMonth: averageOf(singles),
    best: extremeOf(singles, 1),
    worst: extremeOf(singles, -1),
  }
}

export const buildMonthlyProfit = (
  summaries: PortfolioSummaryDto[],
  today: string
): MonthlyProfit | null => {
  const rows = mergeSummariesByDate(summaries)
  if (rows.length < 2) return null
  const groups = monthGroups(rows)
  const months = rankedMonths(
    groups.map((group, index) => figuresOf(group, index === groups.length - 1, today))
  )
  const span = formatMonthSpan(months[0].key, months[months.length - 1].key)
  return { months, span, ...statisticsOf(months) }
}

export const monthOn = (model: MonthlyProfit, key: string): ObservedMonth | undefined =>
  model.months.find(month => month.key === key)

export const moveMonth = (model: MonthlyProfit, key: string, step: number): string | null => {
  const { months } = model
  const next = addMonths(key, step)
  if (next < months[0].key || next > months[months.length - 1].key) return null
  return monthOn(model, next) ? next : moveMonth(model, next, step)
}

export const extremeMonths = ({ best, worst }: MonthlyProfit): ExtremeMonth[] =>
  [
    { label: 'Best month', variant: 'gain-4' as const, month: best },
    { label: 'Worst month', variant: 'loss-4' as const, month: worst },
  ].flatMap(({ label, variant, month }) => (month ? [{ label, variant, month }] : []))

export const formatMonthCell = (percent: number | null): string => {
  if (percent === null) return '—'
  const rounded = Number(percent.toFixed(1)) || 0
  return formatPercentChange(rounded, Math.abs(rounded) >= 100 ? 0 : 1).replace('%', '')
}
