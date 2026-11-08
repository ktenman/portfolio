import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { addDays, daysBetween, formatDayMonth, formatDayRange } from '../utils/iso-dates'

export interface ChartPeriod {
  from: string
  to: string
}

export type PeriodEdge = 'from' | 'to'

export type PeriodSummary = Pick<PortfolioSummaryDto, 'date' | 'totalValue' | 'totalProfit'>

export const MIN_PERIOD_DAYS = 2

const SHIFT_STEP = 30

const KEY_STEPS: Record<string, number> = {
  ArrowLeft: -1,
  ArrowDown: -1,
  ArrowRight: 1,
  ArrowUp: 1,
  PageDown: -SHIFT_STEP,
  PageUp: SHIFT_STEP,
}

const LONG_DAY = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
})

const LONG_DATE = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

const yearOf = (date: string): number => Number(date.slice(0, 4))

const earlier = (first: string, second: string): string => (first < second ? first : second)

const later = (first: string, second: string): string => (first > second ? first : second)

const isWholeYear = ({ from, to }: ChartPeriod): boolean =>
  yearOf(from) === yearOf(to) && from.endsWith('-01-01') && to.endsWith('-12-31')

export const longDate = (date: string): string => LONG_DATE.format(new Date(date))

const longDay = (date: string, withYear: boolean): string =>
  withYear ? longDate(date) : LONG_DAY.format(new Date(date))

export const periodDays = ({ from, to }: ChartPeriod): number => daysBetween(from, to)

export function describePeriod(period: ChartPeriod, today: string): string {
  const { from, to } = period
  const year = yearOf(today)
  if (to === today) return `since ${longDay(from, yearOf(from) !== year)}`
  if (isWholeYear(period)) return `in ${yearOf(from)}`
  if (yearOf(from) !== yearOf(to)) return `from ${longDate(from)} to ${longDate(to)}`
  return `from ${longDay(from, false)} to ${longDay(to, yearOf(to) !== year)}`
}

export function periodLabel(period: ChartPeriod, today: string): string {
  const { from, to } = period
  if (isWholeYear(period)) return String(yearOf(from))
  const thisYear = yearOf(today)
  if (yearOf(from) === thisYear && yearOf(to) === thisYear) {
    return `${formatDayMonth(from)} – ${formatDayMonth(to)}`
  }
  return formatDayRange(from, to)
}

export const chipDate = (date: string, today: string): string =>
  yearOf(date) === yearOf(today) ? formatDayMonth(date) : `${formatDayMonth(date)} ${yearOf(date)}`

const netBuys = (days: PeriodSummary[]): number =>
  days.slice(1).reduce((total, day, index) => {
    const previous = days[index]
    const earned = day.totalProfit - previous.totalProfit
    return total + Math.max(day.totalValue - previous.totalValue - earned, 0)
  }, 0)

const lastOnOrBefore = (summaries: PeriodSummary[], date: string): number => {
  const after = summaries.findIndex(summary => summary.date > date)
  return (after < 0 ? summaries.length : after) - 1
}

export function periodChange(
  summaries: PeriodSummary[],
  period: ChartPeriod
): { amount: number; percent: number } {
  const first = Math.max(lastOnOrBefore(summaries, period.from), 0)
  const last = lastOnOrBefore(summaries, period.to)
  if (last < 0) return { amount: 0, percent: 0 }
  const opening = summaries[first]
  const amount = summaries[last].totalProfit - opening.totalProfit
  const capital = opening.totalValue + netBuys(summaries.slice(first, last + 1))
  return { amount, percent: capital > 0 ? (amount / capital) * 100 : 0 }
}

export function moveEdge(
  period: ChartPeriod,
  edge: PeriodEdge,
  date: string,
  bounds: ChartPeriod
): ChartPeriod {
  if (edge === 'from') {
    const from = later(earlier(date, addDays(period.to, -MIN_PERIOD_DAYS)), bounds.from)
    return { from, to: period.to }
  }
  const to = earlier(later(date, addDays(period.from, MIN_PERIOD_DAYS)), bounds.to)
  return { from: period.from, to }
}

export function slidePeriod(period: ChartPeriod, days: number, bounds: ChartPeriod): ChartPeriod {
  const length = periodDays(period)
  const latest = addDays(bounds.to, -length)
  const from = later(earlier(addDays(period.from, days), latest), bounds.from)
  return { from, to: addDays(from, length) }
}

const keyTarget = (
  date: string,
  key: string,
  shift: boolean,
  bounds: ChartPeriod
): string | null => {
  if (key === 'Home') return bounds.from
  if (key === 'End') return bounds.to
  const step = KEY_STEPS[key]
  if (step === undefined) return null
  return addDays(date, shift && Math.abs(step) === 1 ? step * SHIFT_STEP : step)
}

export function stepEdge(
  period: ChartPeriod,
  edge: PeriodEdge,
  key: string,
  shift: boolean,
  bounds: ChartPeriod
): ChartPeriod | null {
  const target = keyTarget(period[edge], key, shift, bounds)
  return target === null ? null : moveEdge(period, edge, target, bounds)
}

export const spanPeriod = (anchor: string, date: string): ChartPeriod => ({
  from: earlier(anchor, date),
  to: later(anchor, date),
})

export function clampPeriod(period: ChartPeriod, bounds: ChartPeriod): ChartPeriod | null {
  const clamped = { from: later(period.from, bounds.from), to: earlier(period.to, bounds.to) }
  return periodDays(clamped) < MIN_PERIOD_DAYS ? null : clamped
}
