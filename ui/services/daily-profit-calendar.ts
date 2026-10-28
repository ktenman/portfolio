import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'
import {
  formatCurrencyChange,
  formatCurrencyWithSymbol,
  formatPercentChange,
  formatSignedPercent,
} from '../utils/formatters'
import {
  addDays,
  daysBetween,
  formatDayMonth,
  formatFullDate,
  mondayIndex,
  monthName,
  monthsBefore,
} from '../utils/iso-dates'
import { percentOfPreviousValue, sortSummariesByDateAsc } from './summary-aggregator'

export const CHANGE_STEPS = [0.05, 0.3, 0.75, 1.5]
export const FLAT_HINT = `under ±${CHANGE_STEPS[0]}%`

const GAIN_STEPS = ['flat', 'gain-1', 'gain-2', 'gain-3', 'gain-4'] as const
const LOSS_STEPS = ['flat', 'loss-1', 'loss-2', 'loss-3', 'loss-4'] as const

export type ChangeStep = (typeof GAIN_STEPS)[number] | (typeof LOSS_STEPS)[number]

export const STEP_CLASSES: Record<ChangeStep, string> = {
  'loss-4': 'bg-loss text-surface',
  'loss-3': 'bg-loss-step-3 text-ink',
  'loss-2': 'bg-loss-step-2 text-ink',
  'loss-1': 'bg-loss-step-1 text-ink',
  flat: 'bg-change-flat text-ink',
  'gain-1': 'bg-gain-step-1 text-ink',
  'gain-2': 'bg-gain-step-2 text-ink',
  'gain-3': 'bg-gain-step-3 text-ink',
  'gain-4': 'bg-gain text-surface',
}

interface DayTexts {
  date: string
  name: string
  closing: string
  detail: string
  note: string
}

interface CountedDay extends DayTexts {
  kind: 'counted'
  step: ChangeStep
  change: number
  percent: number | null
}

interface UncountedDay extends DayTexts {
  kind: 'starting' | 'missing'
  step: null
  change: null
  percent: null
}

export type CalendarDay = CountedDay | UncountedDay

interface CalendarMonth {
  column: number
  label: string
}

export interface DailyProfitCalendar {
  days: CalendarDay[]
  offset: number
  weeks: number
  months: CalendarMonth[]
  upDays: number
  downDays: number
  flatDays: number
  averageDay: number
  best: CountedDay | null
  worst: CountedDay | null
}

interface ExtremeDay {
  label: string
  variant: ChangeStep
  day: CountedDay
}

interface RowChange {
  row: PortfolioSummaryDto
  change: number
  covered: number
}

const PERIOD_MONTHS = 6
export const CALENDAR_RANGE = TimeRange.SIX_MONTHS
const MONTH_NAME_COLUMNS = 3
export const SEPARATOR = ' · '
const ORDINAL_RULES = new Intl.PluralRules('en-US', { type: 'ordinal' })
const ORDINAL_SUFFIXES: Record<string, string> = { one: 'st', two: 'nd', few: 'rd', other: 'th' }

export const roundToCents = (value: number): number => Number(value.toFixed(2)) || 0

const periodRows = (summaries: PortfolioSummaryDto[], lastDay?: string): PortfolioSummaryDto[] => {
  const sorted = sortSummariesByDateAsc(summaries).filter(row => !lastDay || row.date <= lastDay)
  if (!sorted.length) return []
  const earliestStart = monthsBefore(sorted[sorted.length - 1].date, PERIOD_MONTHS)
  return sorted.filter(row => row.date >= earliestStart)
}

const hasCountedDay = (rows: PortfolioSummaryDto[]): boolean =>
  rows.length > 0 && rows[0].date < rows[rows.length - 1].date

export const periodLastDays = (summaries: PortfolioSummaryDto[], lastDay?: string): string[] => {
  const rows = periodRows(summaries, lastDay)
  if (!hasCountedDay(rows)) return []
  return [rows[rows.length - 1].date, ...periodLastDays(summaries, rows[0].date)]
}

const rowChanges = (rows: PortfolioSummaryDto[]): RowChange[] =>
  rows.slice(1).map((row, index) => ({
    row,
    change: roundToCents(row.totalProfit - rows[index].totalProfit),
    covered: daysBetween(rows[index].date, row.date),
  }))

const isSingle = ({ covered }: RowChange): boolean => covered === 1

export const stepFor = (percent: number, steps: number[], decimals: number): ChangeStep => {
  const printed = Number(Math.abs(percent).toFixed(decimals))
  const reached = steps.filter(step => printed >= step).length
  return (percent > 0 ? GAIN_STEPS : LOSS_STEPS)[reached]
}

const stepOf = (change: number, percent: number | null): ChangeStep => {
  if (percent === null) return change === 0 ? 'flat' : 'gain-1'
  return stepFor(percent, CHANGE_STEPS, 2)
}

const ordinal = (rank: number): string => `${rank}${ORDINAL_SUFFIXES[ORDINAL_RULES.select(rank)]}`

export const rankText = (change: number, changes: number[], unit: string): string => {
  if (change === 0) return ''
  const side = change > 0 ? 'Best' : 'Worst'
  const ahead = changes.filter(other => (change > 0 ? other > change : other < change)).length
  const place = ahead ? `${ordinal(ahead + 1)} ${side.toLowerCase()}` : side
  return `${place} of ${changes.length} ${unit}${changes.length === 1 ? '' : 's'}`
}

const rankOf = (rowChange: RowChange, singleChanges: number[], unit: string): string =>
  isSingle(rowChange) ? rankText(rowChange.change, singleChanges, unit) : ''

export const closingTexts = (row: PortfolioSummaryDto, rank: string, isOpen: boolean) => {
  const verb = isOpen ? 'Now at' : 'Closed at'
  const value = formatCurrencyWithSymbol(row.totalValue)
  const closing = `${verb} ${value}`
  const lead = rank ? `${rank}${SEPARATOR}${verb.toLowerCase()} ${value}` : closing
  const xirr = formatSignedPercent(row.xirrAnnualReturn * 100)
  return { closing, detail: `${lead}${SEPARATOR}XIRR ${xirr}` }
}

export const gapText = (first: string, last: string): string => {
  if (first === last) return formatDayMonth(last)
  const sameMonth = first.slice(0, 7) === last.slice(0, 7)
  return `${sameMonth ? Number(first.slice(8)) : formatDayMonth(first)} to ${formatDayMonth(last)}`
}

const noteOf = ({ row, covered }: RowChange): string => {
  if (covered === 1) return ''
  const gap = gapText(addDays(row.date, 1 - covered), addDays(row.date, -1))
  return `Covers ${covered} days, no daily summary for ${gap}`
}

const missingDay = (date: string): UncountedDay => ({
  date,
  kind: 'missing',
  step: null,
  change: null,
  percent: null,
  name: `${formatFullDate(date)}, no daily summary`,
  closing: '',
  detail: '',
  note: '',
})

const startingDay = (row: PortfolioSummaryDto, isCounted: boolean): UncountedDay => ({
  date: row.date,
  kind: 'starting',
  step: null,
  change: null,
  percent: null,
  name: `${formatFullDate(row.date)}, starting day`,
  ...closingTexts(row, '', false),
  note: isCounted
    ? 'Starting day, counted in the earlier period'
    : 'Starting day, not counted or ranked',
})

const countedDay = (rowChange: RowChange, rank: string, isOpen: boolean): CountedDay => {
  const { row, change } = rowChange
  const percent = percentOfPreviousValue(row.totalValue, change)
  const percentText = percent === null ? [] : [formatPercentChange(percent)]
  return {
    date: row.date,
    kind: 'counted',
    step: stepOf(change, percent),
    change,
    percent,
    name: [formatFullDate(row.date), formatCurrencyChange(change), ...percentText].join(', '),
    ...closingTexts(row, rank, isOpen),
    note: noteOf(rowChange),
  }
}

const countedDays = (changes: RowChange[], openDate: string | null): CountedDay[] => {
  const singleChanges = changes.filter(isSingle).map(({ change }) => change)
  const unit = singleChanges.length === changes.length ? 'day' : 'single day'
  return changes.map(rowChange =>
    countedDay(rowChange, rankOf(rowChange, singleChanges, unit), rowChange.row.date === openDate)
  )
}

const everyDay = (rowDays: CalendarDay[]): CalendarDay[] => {
  const byDate = new Map<string, CalendarDay>(rowDays.map(day => [day.date, day]))
  const start = rowDays[0].date
  const length = daysBetween(start, rowDays[rowDays.length - 1].date) + 1
  const dates = Array.from({ length }, (_, index) => addDays(start, index))
  return dates.map(date => byDate.get(date) ?? missingDay(date))
}

const monthsOf = (days: CalendarDay[], offset: number): CalendarMonth[] => {
  const firsts = days.flatMap((day, index) =>
    day.date.endsWith('-01')
      ? [{ column: Math.floor((offset + index) / 7), label: monthName(day.date) }]
      : []
  )
  const [next] = firsts
  if (next && next.column < MONTH_NAME_COLUMNS) return firsts
  return [{ column: 0, label: monthName(days[0].date) }, ...firsts]
}

const gridOf = (days: CalendarDay[]) => {
  const offset = mondayIndex(days[0].date)
  return { offset, weeks: Math.ceil((offset + days.length) / 7), months: monthsOf(days, offset) }
}

export const countOf = (counted: { step: ChangeStep }[], side: 'gain' | 'loss' | 'flat'): number =>
  counted.filter(day => day.step.startsWith(side)).length

export const extremeOf = <T extends { change: number }>(items: T[], sign: 1 | -1): T | null =>
  items.reduce<T | null>((extreme, item) => {
    const amount = item.change * sign
    return amount > 0 && amount >= (extreme?.change ?? 0) * sign ? item : extreme
  }, null)

const figuresOf = (changes: RowChange[], counted: CountedDay[]) => {
  const singles = counted.filter((_, index) => isSingle(changes[index]))
  return {
    upDays: countOf(counted, 'gain'),
    downDays: countOf(counted, 'loss'),
    flatDays: countOf(counted, 'flat'),
    best: extremeOf(singles, 1),
    worst: extremeOf(singles, -1),
  }
}

export const buildDailyProfitCalendar = (
  summaries: PortfolioSummaryDto[],
  today: string,
  lastDay?: string
): DailyProfitCalendar | null => {
  const rows = periodRows(summaries, lastDay)
  if (rows.length < 2) return null
  const latest = rows[rows.length - 1]
  const changes = rowChanges(rows)
  const counted = countedDays(changes, latest.date === today ? today : null)
  const start = startingDay(rows[0], hasCountedDay(periodRows(summaries, rows[0].date)))
  const days = everyDay([start, ...counted])
  return {
    days,
    ...gridOf(days),
    ...figuresOf(changes, counted),
    averageDay: roundToCents((latest.totalProfit - rows[0].totalProfit) / counted.length),
  }
}

export const dayOn = (calendar: DailyProfitCalendar, date: string): CalendarDay | undefined =>
  calendar.days[daysBetween(calendar.days[0].date, date)]

export const moveDay = (
  calendar: DailyProfitCalendar,
  date: string,
  step: number
): string | null => {
  const { days } = calendar
  const direction = step < 0 ? -1 : 1
  let index = daysBetween(days[0].date, date) + step
  while (days[index]?.kind === 'missing') index += direction
  return days[index]?.date ?? null
}

export const extremeDays = ({ best, worst }: DailyProfitCalendar): ExtremeDay[] =>
  [
    { label: 'Best day', variant: 'gain-4' as const, day: best },
    { label: 'Worst day', variant: 'loss-4' as const, day: worst },
  ].flatMap(({ label, variant, day }) => (day ? [{ label, variant, day }] : []))

export const lacksDayBeforeLatest = (summaries: PortfolioSummaryDto[]): boolean => {
  const dates = sortSummariesByDateAsc(summaries).map(summary => summary.date)
  if (!dates.length) return false
  return !dates.includes(addDays(dates[dates.length - 1], -1))
}
