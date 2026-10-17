import { formatCurrencyChange } from '../utils/formatters'
import { addMonths, formatFullMonth } from '../utils/iso-dates'
import { roundToCents, SEPARATOR } from './daily-profit-calendar'
import {
  coverageOf,
  formatMonthChange,
  formatMonthPercent,
  monthOn,
  percentOf,
  productOf,
  type MonthlyProfit,
  type ObservedMonth,
} from './monthly-profit'

interface MissingMonth {
  kind: 'missing'
  key: string
  name: string
}

type MonthSlot = ObservedMonth | MissingMonth | null

export interface MonthlyYear {
  year: string
  slots: MonthSlot[]
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

const withoutResult = (year: string, slots: MonthSlot[], note: string): MonthlyYear => ({
  year,
  slots,
  percent: null,
  text: '—',
  coverage: '',
  note,
  name: `${year}, ${note.toLowerCase() || 'no daily summary'}`,
})

const resultOf = (year: string, slots: MonthSlot[], months: ObservedMonth[]): MonthlyYear => {
  const { opening } = months[0]
  const { row, isOpen } = months[months.length - 1]
  const change = roundToCents(row.totalProfit - opening.totalProfit)
  const percent = percentOf(productOf(months.map(month => month.factor)))
  const coverage = coverageOf(`${year}-01`, `${year}-12`, opening.date, row.date, isOpen)
  return {
    year,
    slots,
    percent,
    text: percent === null ? '—' : formatMonthPercent(percent),
    coverage,
    note: percent === null ? 'Percentage unavailable' : '',
    name: [
      coverage ? `${year}, ${coverage}` : year,
      formatCurrencyChange(change),
      formatMonthChange(percent, 'percentage unavailable'),
    ].join(', '),
  }
}

const isolates = (year: string, { opening }: ObservedMonth): boolean =>
  opening.date >= `${Number(year) - 1}-12-31`

const yearOf = (model: MonthlyProfit, year: string): MonthlyYear => {
  const slots = Array.from({ length: 12 }, (_, index) =>
    slotOf(model, addMonths(`${year}-01`, index))
  )
  const months = slots.filter(isObserved)
  if (!months.length) return withoutResult(year, slots, '')
  if (!isolates(year, months[0])) return withoutResult(year, slots, UNAVAILABLE)
  return resultOf(year, slots, months)
}

export const monthlyYears = (model: MonthlyProfit): MonthlyYear[] => {
  const first = Number(model.months[0].key.slice(0, 4))
  const last = Number(model.months[model.months.length - 1].key.slice(0, 4))
  return Array.from({ length: last - first + 1 }, (_, index) => yearOf(model, String(last - index)))
}

export const hasMissingMonth = (years: MonthlyYear[]): boolean =>
  years.some(({ slots }) => slots.some(slot => slot?.kind === 'missing'))

export const yearNotes = (years: MonthlyYear[]): string[] =>
  years.flatMap(({ year, coverage, note }) =>
    coverage || note ? [`${year}: ${[coverage, note].filter(Boolean).join(SEPARATOR)}`] : []
  )
