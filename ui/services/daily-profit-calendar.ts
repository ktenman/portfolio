import type { PortfolioSummaryDto } from '../models/generated/domain-models'
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

export const CHANGE_STEPS = [0.05, 0.25, 0.5, 1]
export const FLAT_HINT = `under ±${CHANGE_STEPS[0]}%`

export type ChangeStep =
  | 'flat'
  | 'gain-1'
  | 'gain-2'
  | 'gain-3'
  | 'gain-4'
  | 'loss-1'
  | 'loss-2'
  | 'loss-3'
  | 'loss-4'

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
const MONTH_NAME_COLUMNS = 3
const SEPARATOR = ' · '
const GAIN_STEPS: ChangeStep[] = ['flat', 'gain-1', 'gain-2', 'gain-3', 'gain-4']
const LOSS_STEPS: ChangeStep[] = ['flat', 'loss-1', 'loss-2', 'loss-3', 'loss-4']
const ORDINAL_RULES = new Intl.PluralRules('en-US', { type: 'ordinal' })
const ORDINAL_SUFFIXES: Record<string, string> = { one: 'st', two: 'nd', few: 'rd', other: 'th' }

const roundToCents = (value: number): number => Number(value.toFixed(2)) || 0

const periodRows = (summaries: PortfolioSummaryDto[]): PortfolioSummaryDto[] => {
  const sorted = sortSummariesByDateAsc(summaries)
  if (!sorted.length) return []
  const earliestStart = monthsBefore(sorted[sorted.length - 1].date, PERIOD_MONTHS)
  return sorted.filter(row => row.date >= earliestStart)
}

const rowChanges = (rows: PortfolioSummaryDto[]): RowChange[] =>
  rows.slice(1).map((row, index) => ({
    row,
    change: roundToCents(row.totalProfit - rows[index].totalProfit),
    covered: daysBetween(rows[index].date, row.date),
  }))

const isSingle = ({ covered }: RowChange): boolean => covered === 1

const stepOf = (change: number, percent: number | null): ChangeStep => {
  if (percent === null) return change === 0 ? 'flat' : 'gain-1'
  const printed = Number(Math.abs(percent).toFixed(2))
  const reached = CHANGE_STEPS.filter(step => printed >= step).length
  return (change > 0 ? GAIN_STEPS : LOSS_STEPS)[reached]
}

const ordinal = (rank: number): string => `${rank}${ORDINAL_SUFFIXES[ORDINAL_RULES.select(rank)]}`

const rankOf = (rowChange: RowChange, singleChanges: number[]): string => {
  const { change } = rowChange
  if (!isSingle(rowChange) || change === 0) return ''
  const side = change > 0 ? 'Best' : 'Worst'
  const ahead = singleChanges.filter(other => (change > 0 ? other > change : other < change)).length
  const place = ahead ? `${ordinal(ahead + 1)} ${side.toLowerCase()}` : side
  return `${place} of ${singleChanges.length} single day${singleChanges.length === 1 ? '' : 's'}`
}

const closingTexts = (row: PortfolioSummaryDto, rank: string, isOpen: boolean) => {
  const verb = isOpen ? 'Now at' : 'Closed at'
  const value = formatCurrencyWithSymbol(row.totalValue)
  const closing = `${verb} ${value}`
  const lead = rank ? `${rank}${SEPARATOR}${verb.toLowerCase()} ${value}` : closing
  const xirr = formatSignedPercent(row.xirrAnnualReturn * 100)
  return { closing, detail: `${lead}${SEPARATOR}XIRR ${xirr}` }
}

const gapText = (first: string, last: string): string => {
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

const startingDay = (row: PortfolioSummaryDto): UncountedDay => ({
  date: row.date,
  kind: 'starting',
  step: null,
  change: null,
  percent: null,
  name: `${formatFullDate(row.date)}, starting day`,
  ...closingTexts(row, '', false),
  note: 'Starting day, not counted or ranked',
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
  return changes.map(rowChange =>
    countedDay(rowChange, rankOf(rowChange, singleChanges), rowChange.row.date === openDate)
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

const countOf = (counted: CountedDay[], side: 'gain' | 'loss' | 'flat'): number =>
  counted.filter(day => day.step.startsWith(side)).length

const extremeOf = (singles: CountedDay[], sign: 1 | -1): CountedDay | null =>
  singles.reduce<CountedDay | null>((extreme, day) => {
    const amount = day.change * sign
    return amount > 0 && amount >= (extreme?.change ?? 0) * sign ? day : extreme
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
  today: string
): DailyProfitCalendar | null => {
  const rows = periodRows(summaries)
  if (rows.length < 2) return null
  const latest = rows[rows.length - 1]
  const changes = rowChanges(rows)
  const counted = countedDays(changes, latest.date === today ? today : null)
  const days = everyDay([startingDay(rows[0]), ...counted])
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
