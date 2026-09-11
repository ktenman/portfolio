import { DONUT_COLORS } from '../constants/chart-colors'

export interface BreakdownItem {
  label: string
  value: number
  code?: string
}

export interface ComparedRow {
  label: string
  value: number
  benchmark?: number
  ratio?: number
  code?: string
  isOther: boolean
}

export type BreakdownRow = ComparedRow & { color: string }

export type BreakdownView = 'donut' | 'bars'

export interface CompareOptions {
  topCount: number
  minPercentage: number
  withOther: boolean
}

export const TOP_COUNT = 15
export const INDUSTRY_TOP_COUNT = 30
export const SECTOR_MIN_PERCENTAGE = 0.5
export const COUNTRY_MIN_PERCENTAGE = 0.2
export const INDUSTRY_MIN_PERCENTAGE = 0.1
const MIN_BENCHMARK_SHARE = 0.005

export const paint = (rows: ComparedRow[]): BreakdownRow[] =>
  rows.map((row, index) => ({ ...row, color: DONUT_COLORS[index % DONUT_COLORS.length] }))

export const optionsForView = (options: CompareOptions, view: BreakdownView): CompareOptions =>
  view === 'donut' && options.topCount > TOP_COUNT ? { ...options, topCount: TOP_COUNT } : options

export const isFlagged = (ratio: number | undefined): boolean =>
  ratio !== undefined && (ratio > 2 || ratio < 0.5)

export const unlessAbsent = (row: ComparedRow): ComparedRow =>
  row.benchmark === 0 ? { ...row, benchmark: undefined, ratio: undefined } : row

export const formatBenchmarkShare = (
  benchmark: number | undefined,
  ratio: number | undefined
): string => {
  const share = `${(benchmark ?? 0).toFixed(2)}%`
  return ratio === undefined ? share : `${share} · ${ratio.toFixed(2)}×`
}

const normaliseLabel = (label: string): string => label.toLowerCase().replace(/\s+/g, ' ').trim()

const toMap = (items: BreakdownItem[]): Map<string, number> =>
  items.reduce((map, item) => {
    const key = normaliseLabel(item.label)
    return map.set(key, (map.get(key) ?? 0) + item.value)
  }, new Map<string, number>())

const residual = (map: Map<string, number>, shown: Set<string>): number =>
  Array.from(map.entries())
    .filter(([key]) => !shown.has(key))
    .reduce((sum, [, value]) => sum + value, 0)

const ratioOf = (value: number, share: number): number | undefined =>
  share >= MIN_BENCHMARK_SHARE ? value / share : undefined

export function compareBreakdown(
  items: BreakdownItem[],
  benchmarkItems: BreakdownItem[] | null,
  options: CompareOptions
): ComparedRow[] {
  const itemsMap = toMap(items)
  const shown = items
    .filter(item => item.value >= options.minPercentage)
    .sort((a, b) => b.value - a.value)
    .slice(0, options.topCount)
  const shownKeys = new Set(shown.map(item => normaliseLabel(item.label)))
  const benchmarkMap = benchmarkItems === null ? null : toMap(benchmarkItems)
  const rows: ComparedRow[] = shown.map(item => {
    const row: ComparedRow = {
      label: item.label,
      value: item.value,
      code: item.code,
      isOther: false,
    }
    if (benchmarkMap === null) return row
    const share = benchmarkMap.get(normaliseLabel(item.label)) ?? 0
    return { ...row, benchmark: share, ratio: ratioOf(item.value, share) }
  })
  if (!options.withOther) return rows
  const other = residual(itemsMap, shownKeys)
  const benchmarkOther = benchmarkMap === null ? 0 : residual(benchmarkMap, shownKeys)
  if (other === 0 && benchmarkOther === 0) return rows
  const otherRow: ComparedRow = { label: 'Other', value: other, isOther: true }
  return [...rows, benchmarkMap === null ? otherRow : { ...otherRow, benchmark: benchmarkOther }]
}

const toWeights = (items: BreakdownItem[]): Map<string, number> => {
  const map = toMap(items)
  const total = Array.from(map.values()).reduce((sum, value) => sum + value, 0)
  if (total === 0) return map
  return new Map(Array.from(map.entries()).map(([key, value]) => [key, (value / total) * 100]))
}

export function activeShare(items: BreakdownItem[], benchmarkItems: BreakdownItem[]): number {
  const weights = toWeights(items)
  const benchmark = toWeights(benchmarkItems)
  const labels = new Set([...weights.keys(), ...benchmark.keys()])
  const total = Array.from(labels).reduce(
    (sum, label) => sum + Math.abs((weights.get(label) ?? 0) - (benchmark.get(label) ?? 0)),
    0
  )
  return total / 2
}
