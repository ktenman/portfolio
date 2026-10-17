import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import { addMonths, formatDayMonth, formatFullMonth } from '../utils/iso-dates'
import { roundToCents } from './daily-profit-calendar'
import { monthOn, type MonthlyProfit, type ObservedMonth } from './monthly-profit'

export interface MissingMonth {
  kind: 'missing'
  key: string
  name: string
}

export type MonthSlot = ObservedMonth | MissingMonth | null

export interface MonthlyYear {
  year: string
  slots: MonthSlot[]
  change: number | null
  percent: number | null
  text: string
  coverage: string
  note: string
  name: string
}

const UNAVAILABLE = 'Year result unavailable: missing year-end summary'

const slotOf = (model: MonthlyProfit, key: string): MonthSlot => {
  const { months } = model
  if (key < months[0].key || key > months[months.length - 1].key) return null
  return (
    monthOn(model, key) ?? {
      kind: 'missing',
      key,
      name: `${formatFullMonth(key)}, no daily summary`,
    }
  )
}

const isObserved = (slot: MonthSlot): slot is ObservedMonth => slot?.kind === 'observed'

const withoutResult = (
  year: string,
  slots: MonthSlot[],
  note: string,
  name: string
): MonthlyYear => ({
  year,
  slots,
  change: null,
  percent: null,
  text: '—',
  coverage: '',
  note,
  name,
})

const percentOf = (months: ObservedMonth[]): number | null => {
  const product = months.reduce<number | null>(
    (total, { factor }) => (total === null || factor === null ? null : total * factor),
    1
  )
  return product === null ? null : 100 * (product - 1)
}

const coverageOf = (year: string, first: ObservedMonth, last: ObservedMonth): string => {
  const opening = first.opening.date
  const closing = last.row.date
  const from = opening.startsWith(year) ? `from ${formatDayMonth(opening)}` : ''
  const to = closing !== `${year}-12-31` || last.isOpen ? `to ${formatDayMonth(closing)}` : ''
  return [from, to].filter(Boolean).join(' ')
}

const resultOf = (year: string, slots: MonthSlot[], months: ObservedMonth[]): MonthlyYear => {
  const first = months[0]
  const last = months[months.length - 1]
  const change = roundToCents(last.row.totalProfit - first.opening.totalProfit)
  const percent = percentOf(months)
  const coverage = coverageOf(year, first, last)
  const percentText = percent === null ? 'percentage unavailable' : formatPercentChange(percent, 1)
  return {
    year,
    slots,
    change,
    percent,
    text: percent === null ? '—' : percentText,
    coverage,
    note: percent === null ? 'Percentage unavailable' : '',
    name: [
      coverage ? `${year}, ${coverage}` : year,
      formatCurrencyChange(change),
      percentText,
    ].join(', '),
  }
}

const isolates = (year: string, { opening }: ObservedMonth): boolean =>
  opening.date.startsWith(year) || opening.date === `${Number(year) - 1}-12-31`

const yearOf = (model: MonthlyProfit, year: string): MonthlyYear => {
  const slots = Array.from({ length: 12 }, (_, index) =>
    slotOf(model, addMonths(`${year}-01`, index))
  )
  const months = slots.filter(isObserved)
  if (!months.length) return withoutResult(year, slots, '', `${year}, no daily summary`)
  if (!isolates(year, months[0])) {
    return withoutResult(
      year,
      slots,
      UNAVAILABLE,
      `${year}, year result unavailable: missing year-end summary`
    )
  }
  return resultOf(year, slots, months)
}

export const monthlyYears = (model: MonthlyProfit): MonthlyYear[] => {
  const first = Number(model.months[0].key.slice(0, 4))
  const last = Number(model.months[model.months.length - 1].key.slice(0, 4))
  return Array.from({ length: last - first + 1 }, (_, index) => yearOf(model, String(last - index)))
}

export const hasMissingMonth = (years: MonthlyYear[]): boolean =>
  years.some(({ slots }) => slots.some(slot => slot?.kind === 'missing'))
