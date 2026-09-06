import { MIN_BENCHMARK_SHARE } from './etf-chart-service'

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

export interface CompareOptions {
  topCount: number
  minPercentage: number
  withOther: boolean
}

export const INDUSTRY_TOP_COUNT = 40
export const INDUSTRY_MIN_PERCENTAGE = 0.1

export const isFlagged = (ratio: number | undefined): boolean =>
  ratio !== undefined && (ratio > 2 || ratio < 0.5)

export const normaliseLabel = (label: string): string =>
  label.toLowerCase().replace(/\s+/g, ' ').trim()

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
  const shown = [...items]
    .sort((a, b) => b.value - a.value)
    .filter(item => item.value >= options.minPercentage)
    .slice(0, options.topCount)
  const shownKeys = new Set(shown.map(item => normaliseLabel(item.label)))
  const benchmarkMap = benchmarkItems === null ? null : toMap(benchmarkItems)
  const rows: ComparedRow[] = shown.map(item => {
    const row: ComparedRow = { label: item.label, value: item.value, code: item.code, isOther: false }
    if (benchmarkMap === null) return row
    const share = benchmarkMap.get(normaliseLabel(item.label)) ?? 0
    return { ...row, benchmark: share, ratio: ratioOf(item.value, share) }
  })
  if (!options.withOther) return rows
  const other = residual(toMap(items), shownKeys)
  const benchmarkOther = benchmarkMap === null ? 0 : residual(benchmarkMap, shownKeys)
  if (other === 0 && benchmarkOther === 0) return rows
  const otherRow: ComparedRow = { label: 'Other', value: other, isOther: true }
  return [...rows, benchmarkMap === null ? otherRow : { ...otherRow, benchmark: benchmarkOther }]
}

export function activeShare(items: BreakdownItem[], benchmarkItems: BreakdownItem[]): number {
  const weights = toMap(items)
  const benchmark = toMap(benchmarkItems)
  const labels = new Set([...weights.keys(), ...benchmark.keys()])
  const total = Array.from(labels).reduce(
    (sum, label) => sum + Math.abs((weights.get(label) ?? 0) - (benchmark.get(label) ?? 0)),
    0
  )
  return total / 2
}
