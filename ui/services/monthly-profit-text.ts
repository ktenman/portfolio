import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import {
  formatCurrencyChange,
  formatCurrencyWithSymbol,
  formatSignedPercent,
} from '../utils/formatters'
import { addDays, daysBetween, formatDayMonth } from '../utils/iso-dates'
import type { ProfitMonth } from './monthly-profit'

const SHORT_MONTH = new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' })
const FULL_MONTH = new Intl.DateTimeFormat('en-US', { month: 'long', timeZone: 'UTC' })
const ORDINAL_RULES = new Intl.PluralRules('en-US', { type: 'ordinal' })
const ORDINAL_SUFFIXES: Record<string, string> = { one: 'st', two: 'nd', few: 'rd', other: 'th' }

export const formatMonth = (key: string, full = false): string => {
  const date = new Date(`${key}-01T00:00:00Z`)
  return `${(full ? FULL_MONTH : SHORT_MONTH).format(date)} ${key.slice(0, 4)}`
}

export const formatMonthlyPercent = (percent: number | null, grid = false): string => {
  if (percent === null || !Number.isFinite(percent)) return '—'
  const precision = grid && Math.abs(percent) >= 100 ? 0 : 1
  const rounded = Number(percent.toFixed(precision)) + 0
  const sign = rounded > 0 ? '+' : rounded < 0 ? '−' : ''
  return `${sign}${Math.abs(rounded).toFixed(precision)}${grid ? '' : '%'}`
}

const fullDate = (date: string): string => `${formatDayMonth(date)} ${date.slice(0, 4)}`

const monthEnd = (date: string): string =>
  new Date(Date.UTC(Number(date.slice(0, 4)), Number(date.slice(5, 7)), 0))
    .toISOString()
    .slice(0, 10)

const monthsCovered = (opening: string, closing: string): number => {
  const first = addDays(opening, 1)
  return (
    (Number(closing.slice(0, 4)) - Number(first.slice(0, 4))) * 12 +
    Number(closing.slice(5, 7)) -
    Number(first.slice(5, 7)) +
    1
  )
}

const gapNote = (opening: string, closing: string): string[] => {
  if (daysBetween(opening, closing) <= 1) return []
  const first = addDays(opening, 1)
  const last = addDays(closing, -1)
  const range = first === last ? fullDate(first) : `${fullDate(first)} – ${fullDate(last)}`
  return [`No daily summary for ${range}`]
}

const partialMonth = (opening: string, closing: string, today: string): string => {
  const from = opening.slice(0, 7) === closing.slice(0, 7) ? `from ${formatDayMonth(opening)}` : ''
  const to = closing === today || closing < monthEnd(closing) ? `to ${formatDayMonth(closing)}` : ''
  return [from, to].filter(Boolean).join(' ')
}

export const monthCoverage = (
  opening: PortfolioSummaryDto,
  rows: PortfolioSummaryDto[],
  today: string,
  eligible: boolean
): string[] => {
  const closing = rows[rows.length - 1]
  const interval = eligible
    ? partialMonth(opening.date, closing.date, today)
    : `from ${fullDate(opening.date)} to ${fullDate(closing.date)}`
  const span = eligible ? [] : [`Covers ${monthsCovered(opening.date, closing.date)} months`]
  const gaps = rows.flatMap((row, index) =>
    gapNote(index ? rows[index - 1].date : opening.date, row.date)
  )
  const tail =
    closing.date !== today && closing.date < monthEnd(closing.date)
      ? [`No daily summary after ${formatDayMonth(closing.date)}`]
      : []
  return [interval, ...span, ...gaps, ...tail].filter(Boolean)
}

export const closingLabel = (row: PortfolioSummaryDto, today: string): string => {
  const value = formatCurrencyWithSymbol(row.totalValue)
  return row.date === today ? `Now at ${value}` : `Closed at ${value} · as of ${fullDate(row.date)}`
}

const euroChange = (change: number | null): string =>
  change !== null && Number.isFinite(change) ? formatCurrencyChange(change) : 'Change unavailable'

const percentage = (percent: number | null): string =>
  percent === null ? 'Percentage unavailable' : formatMonthlyPercent(percent)

export const monthDescription = (month: ProfitMonth): string =>
  [
    formatMonth(month.key, true),
    euroChange(month.change),
    percentage(month.percent),
    month.closingLabel,
    `XIRR ${formatSignedPercent(month.closing.xirrAnnualReturn * 100)}`,
    month.rank ? `${month.rank} (ranked by euro profit)` : '',
    ...month.coverage,
  ]
    .filter(Boolean)
    .join(', ')

export const monthRank = (
  month: ProfitMonth,
  singles: ProfitMonth[],
  excluded: boolean
): string => {
  if (!month.eligible || month.change === 0 || !Number.isFinite(month.change)) return ''
  const best = month.change > 0
  const ahead = singles.filter(
    other =>
      Number.isFinite(other.change) &&
      (best ? other.change > month.change : other.change < month.change)
  ).length
  const side = best ? 'Best' : 'Worst'
  const ordinal = `${ahead + 1}${ORDINAL_SUFFIXES[ORDINAL_RULES.select(ahead + 1)]}`
  const place = ahead ? `${ordinal} ${side.toLowerCase()}` : side
  const unit = excluded ? 'single month' : 'month'
  return `${place} of ${singles.length} ${unit}${singles.length === 1 ? '' : 's'}`
}

export const monthlySpan = (months: ProfitMonth[]): string => {
  const first = months[0].key
  const last = months[months.length - 1].key
  if (first === last) return formatMonth(first)
  const start =
    first.slice(0, 4) === last.slice(0, 4) ? formatMonth(first).split(' ')[0] : formatMonth(first)
  return `${start} – ${formatMonth(last)}`
}

export const yearCoverage = (
  year: number,
  months: ProfitMonth[],
  today: string,
  latest: boolean,
  isolated: boolean
): string[] => {
  if (!months.length) return [`No daily summary in ${year}`]
  const opening = months[0].opening.date
  const closing = months[months.length - 1].closing.date
  const from = opening === `${year - 1}-12-31` ? [] : [`from ${fullDate(opening)}`]
  const to =
    closing < `${year}-12-31` || closing === today
      ? [`${latest ? 'YTD to' : 'to'} ${fullDate(closing)}`]
      : []
  const unavailable = isolated ? [] : ['Year result unavailable: missing year-end summary']
  return [...from, ...to, ...unavailable]
}

export const yearDescription = (
  year: number,
  change: number | null,
  percent: number | null,
  coverage: string[],
  observed: boolean
): string => {
  if (!observed) return `${year}, no daily summary`
  return [String(year), euroChange(change), percentage(percent), ...coverage].join(', ')
}
