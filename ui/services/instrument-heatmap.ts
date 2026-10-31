import { TimeRange, type InstrumentDto } from '../models/generated/domain-models'
import {
  formatCurrencyChange,
  formatCurrencyWithSymbol,
  formatPercentChange,
  formatPercentage,
} from '../utils/formatters'
import { CHANGE_STEPS } from './daily-profit-calendar'
import { MONTH_STEPS } from './monthly-profit'

export interface HeatmapBox<T> {
  item: T
  x: number
  y: number
  width: number
  height: number
}

export type HeatmapTier = 'xl' | 'lg' | 'md' | 'sm' | 'xs'

interface Area<T> {
  item: T
  area: number
}

type Rect = Omit<HeatmapBox<never>, 'item'>

const DRAWN_SHARE = 0.001
const WEEK_STEPS = [0.1, 0.7, 1.7, 3.4]
const YEAR_STEPS = [1, 5, 12, 25]
const YEARS_STEPS = [2, 10, 25, 50]

export const HEATMAP_STEPS: Record<TimeRange, number[]> = {
  [TimeRange.ONE_DAY]: CHANGE_STEPS,
  [TimeRange.TWO_DAYS]: WEEK_STEPS,
  [TimeRange.THREE_DAYS]: WEEK_STEPS,
  [TimeRange.FOUR_DAYS]: WEEK_STEPS,
  [TimeRange.FIVE_DAYS]: WEEK_STEPS,
  [TimeRange.SIX_DAYS]: WEEK_STEPS,
  [TimeRange.ONE_WEEK]: WEEK_STEPS,
  [TimeRange.TWO_WEEKS]: WEEK_STEPS,
  [TimeRange.THREE_WEEKS]: MONTH_STEPS,
  [TimeRange.FOUR_WEEKS]: MONTH_STEPS,
  [TimeRange.FIVE_WEEKS]: MONTH_STEPS,
  [TimeRange.SIX_WEEKS]: MONTH_STEPS,
  [TimeRange.ONE_MONTH]: MONTH_STEPS,
  [TimeRange.TWO_MONTHS]: MONTH_STEPS,
  [TimeRange.THREE_MONTHS]: MONTH_STEPS,
  [TimeRange.FOUR_MONTHS]: YEAR_STEPS,
  [TimeRange.FIVE_MONTHS]: YEAR_STEPS,
  [TimeRange.SIX_MONTHS]: YEAR_STEPS,
  [TimeRange.YTD]: YEAR_STEPS,
  [TimeRange.ONE_YEAR]: YEAR_STEPS,
  [TimeRange.TWO_YEARS]: YEARS_STEPS,
  [TimeRange.THREE_YEARS]: YEARS_STEPS,
  [TimeRange.FOUR_YEARS]: YEARS_STEPS,
  [TimeRange.FIVE_YEARS]: YEARS_STEPS,
  [TimeRange.SIX_YEARS]: YEARS_STEPS,
  [TimeRange.MAX]: YEARS_STEPS,
}

const TIER_MINIMUMS: Record<'wide' | 'narrow', [HeatmapTier, number, number][]> = {
  wide: [
    ['xl', 210, 186],
    ['lg', 116, 106],
    ['md', 70, 72],
    ['sm', 48, 36],
  ],
  narrow: [
    ['lg', 86, 112],
    ['md', 64, 66],
    ['sm', 44, 34],
  ],
}

const sumOf = <T>(row: Area<T>[]): number => row.reduce((sum, { area }) => sum + area, 0)

const worst = <T>(row: Area<T>[], side: number): number => {
  const sum = sumOf(row)
  const areas = row.map(({ area }) => area)
  return Math.max(
    (side * side * Math.max(...areas)) / (sum * sum),
    (sum * sum) / (side * side * Math.min(...areas))
  )
}

const place = <T>(row: Area<T>[], rect: Rect): { boxes: HeatmapBox<T>[]; rest: Rect } => {
  const vertical = rect.width >= rect.height
  const thickness = sumOf(row) / (vertical ? rect.height : rect.width)
  const boxes = row.reduce<HeatmapBox<T>[]>((placed, { item, area }) => {
    const last = placed[placed.length - 1]
    const length = area / thickness
    if (vertical) {
      const y = last ? last.y + last.height : rect.y
      return [...placed, { item, x: rect.x, y, width: thickness, height: length }]
    }
    const x = last ? last.x + last.width : rect.x
    return [...placed, { item, x, y: rect.y, width: length, height: thickness }]
  }, [])
  const rest = vertical
    ? { ...rect, x: rect.x + thickness, width: rect.width - thickness }
    : { ...rect, y: rect.y + thickness, height: rect.height - thickness }
  return { boxes, rest }
}

const layout = <T>(areas: Area<T>[], rect: Rect, row: Area<T>[] = []): HeatmapBox<T>[] => {
  const [next, ...rest] = areas
  if (!next) return place(row, rect).boxes
  const side = Math.min(rect.width, rect.height)
  if (row.length === 0 || worst([...row, next], side) <= worst(row, side)) {
    return layout(rest, rect, [...row, next])
  }
  const placed = place(row, rect)
  return [...placed.boxes, ...layout(areas, placed.rest)]
}

export const squarify = <T>(
  items: { item: T; value: number }[],
  width: number,
  height: number
): HeatmapBox<T>[] => {
  const sorted = items.filter(({ value }) => value > 0).sort((a, b) => b.value - a.value)
  const total = sorted.reduce((sum, { value }) => sum + value, 0)
  if (sorted.length === 0 || width <= 0 || height <= 0) return []
  const scale = (width * height) / total
  return layout(
    sorted.map(({ item, value }) => ({ item, area: value * scale })),
    { x: 0, y: 0, width, height }
  )
}

export const tierFor = (width: number, height: number, wide: boolean): HeatmapTier =>
  TIER_MINIMUMS[wide ? 'wide' : 'narrow'].find(
    ([, minWidth, minHeight]) => width >= minWidth && height >= minHeight
  )?.[0] ?? 'xs'

export const tickerOf = (symbol: string): string => symbol.split(':')[0]

const valueOf = (instrument: InstrumentDto): number => instrument.currentValue ?? 0

export const drawablePositions = (instruments: InstrumentDto[]): InstrumentDto[] => {
  const total = instruments.reduce((sum, instrument) => sum + valueOf(instrument), 0)
  return instruments
    .filter(instrument => total > 0 && valueOf(instrument) / total >= DRAWN_SHARE)
    .sort((a, b) => valueOf(b) - valueOf(a))
}

const amountOf = (instrument: InstrumentDto): number =>
  instrument.priceChangeAmount ?? Number.NEGATIVE_INFINITY

export const changeOrder = (instruments: InstrumentDto[]): InstrumentDto[] =>
  [...instruments].sort((a, b) => amountOf(b) - amountOf(a) || 0)

export const barShare = (amount: number | null | undefined, largest: number): number =>
  amount == null || largest === 0 ? 0 : Math.abs(amount) / largest

const changeText = (instrument: InstrumentDto, period: TimeRange): string => {
  const { priceChangePercent: percent, priceChangeAmount: amount } = instrument
  if (percent == null || amount == null) return `No ${period} change.`
  return `${period} change ${formatPercentChange(percent)}, ${formatCurrencyChange(amount)}.`
}

export const boxLabel = (instrument: InstrumentDto, weight: number, period: TimeRange): string =>
  `${tickerOf(instrument.symbol)}, ${instrument.name}. Value ${formatCurrencyWithSymbol(
    instrument.currentValue
  )}, ${formatPercentage(weight)} of the portfolio. ${changeText(instrument, period)}`
