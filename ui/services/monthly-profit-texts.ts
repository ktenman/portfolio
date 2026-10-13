import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import {
  addDays,
  addMonths,
  daysBetween,
  formatDayMonth,
  formatFullMonth,
  formatMonthSpan,
  lastDayOfMonth,
  monthKey,
  monthsBetween,
} from '../utils/iso-dates'
import { closingTexts, gapText, type ChangeStep } from './daily-profit-calendar'
import type { MonthInterval } from './monthly-profit'

export const MONTH_STEPS = [0.3, 1, 2.5, 5]
export const MONTH_FLAT_HINT = `under ±${MONTH_STEPS[0]}%`
export const NO_PERCENT = 'Percentage unavailable'

const GAIN_STEPS = ['flat', 'gain-1', 'gain-2', 'gain-3', 'gain-4'] as const
const LOSS_STEPS = ['flat', 'loss-1', 'loss-2', 'loss-3', 'loss-4'] as const
const MAX_NAMED_GAPS = 3
const WHOLE_NUMBER_FROM = 100

const sideStep = (amount: number): ChangeStep => {
  if (amount === 0) return 'flat'
  return amount > 0 ? 'gain-1' : 'loss-1'
}

export const stepOfMonth = (change: number, percent: number | null): ChangeStep => {
  if (percent === null) return sideStep(change)
  const printed = Number(Math.abs(percent).toFixed(1))
  const reached = MONTH_STEPS.filter(step => printed >= step).length
  return (percent > 0 ? GAIN_STEPS : LOSS_STEPS)[reached]
}

export const cellTextOf = (percent: number | null): string => {
  if (percent === null) return '—'
  const rounded = Number(percent.toFixed(1)) || 0
  const digits = Math.abs(rounded) >= WHOLE_NUMBER_FROM ? 0 : 1
  const sign = rounded > 0 ? '+' : rounded < 0 ? '−' : ''
  return `${sign}${Math.abs(rounded).toFixed(digits)}`
}

const coverageOf = ({ key, opening, closing, ...interval }: MonthInterval, today: string) => {
  if (!interval.isSingleMonth) {
    return `${formatDayMonth(addDays(opening.date, 1))} – ${formatDayMonth(closing.date)}`
  }
  const from = interval.isFromBaseline ? [`from ${formatDayMonth(opening.date)}`] : []
  const isPartial = closing.date < lastDayOfMonth(key) || closing.date === today
  return [...from, ...(isPartial ? [`to ${formatDayMonth(closing.date)}`] : [])].join(' ')
}

const spanNotes = ({ key, opening, isSingleMonth }: MonthInterval): string[] => {
  if (isSingleMonth) return []
  const count = monthsBetween(monthKey(addDays(opening.date, 1)), key) + 1
  const firstMissing = addMonths(monthKey(opening.date), 1)
  const lastMissing = addMonths(key, -1)
  const missing =
    firstMissing <= lastMissing
      ? `, no daily summary for ${formatMonthSpan(firstMissing, lastMissing)}`
      : ''
  return [`Covers ${count} months${missing}`]
}

const earlyCloseNotes = ({ key, closing, isLatest }: MonthInterval): string[] =>
  !isLatest && closing.date < lastDayOfMonth(key)
    ? [`No daily summary after ${formatDayMonth(closing.date)}`]
    : []

const missingDates = ({ key, opening, closing, dates }: MonthInterval): string[] => {
  const firstCovered = addDays(opening.date, 1)
  const start = firstCovered > `${key}-01` ? firstCovered : `${key}-01`
  const observed = new Set(dates)
  return Array.from({ length: daysBetween(start, closing.date) + 1 }, (_, index) =>
    addDays(start, index)
  ).filter(date => !observed.has(date))
}

const gapsOf = (dates: string[]): string[][] =>
  dates.reduce<string[][]>((gaps, date) => {
    const last = gaps[gaps.length - 1]
    if (last && addDays(last[last.length - 1], 1) === date) {
      return [...gaps.slice(0, -1), [...last, date]]
    }
    return [...gaps, [date]]
  }, [])

const gapNotes = (interval: MonthInterval): string[] => {
  const dates = missingDates(interval)
  if (!dates.length) return []
  const gaps = gapsOf(dates)
  const named =
    gaps.length > MAX_NAMED_GAPS
      ? `${dates.length} days`
      : gaps.map(gap => gapText(gap[0], gap[gap.length - 1])).join(', ')
  return [`No daily summary for ${named}`]
}

export const textsOf = (
  interval: MonthInterval,
  percent: number | null,
  today: string,
  rank: string
) => {
  const { key, closing, change } = interval
  const label = formatFullMonth(key)
  const coverage = coverageOf(interval, today)
  const percentText = percent === null ? NO_PERCENT : formatPercentChange(percent, 1)
  return {
    step: stepOfMonth(change, percent),
    cell: cellTextOf(percent),
    label,
    coverage,
    notes: [
      ...spanNotes(interval),
      ...earlyCloseNotes(interval),
      ...gapNotes(interval),
      ...(percent === null ? [NO_PERCENT] : []),
    ],
    detail: closingTexts(closing, rank, closing.date === today).detail,
    name: [label, ...(coverage ? [coverage] : []), formatCurrencyChange(change), percentText].join(
      ', '
    ),
  }
}
