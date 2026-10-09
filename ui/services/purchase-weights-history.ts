import {
  TransactionType,
  type EtfDetailDto,
  type TransactionResponseDto,
} from '../models/generated/domain-models'
import { formatCurrencyWithSymbol } from '../utils/formatters'

export type DayKind = 'buy' | 'swap' | 'cash'
export type WeightMetric = 'invested' | 'bought'
export type WeightCell = number | 'sold' | null

export interface PurchaseDay {
  date: string
  kind: DayKind
  day: number
  month: string
  year: number | null
  label: string
  title: string
}

export interface PurchaseRow {
  instrumentId: number
  symbol: string
  invested: WeightCell[]
  bought: WeightCell[]
}

export interface PurchaseWeights {
  days: PurchaseDay[]
  rows: PurchaseRow[]
}

interface Trade {
  owner: number
  cash: boolean
  date: string
  id: number
  position: string
  sell: boolean
  quantity: number
  amount: number
}

interface Position {
  owner: number
  quantity: number
  cost: number
}

interface Snapshot {
  costs: Map<number, number>
  nets: Map<number, number>
}

export const KIND_LABELS = { buy: 'Buy', swap: 'Swap or rebalance', cash: 'Cash' }
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const CASH_SYMBOL = 'CASH'
const DARKEST_SHARE = 40
const DARKEST_TINT = 55
const SMALLEST_SHARE = 0.05
const HALF_CENT = 0.005

export const shade = (share: number): number =>
  (Math.min(share, DARKEST_SHARE) / DARKEST_SHARE) * DARKEST_TINT

const sum = (values: Iterable<number>): number =>
  [...values].reduce((total, value) => total + value, 0)

const totalsByOwner = (entries: Array<[number, number]>): Map<number, number> =>
  entries.reduce(
    (totals, [owner, value]) => totals.set(owner, (totals.get(owner) ?? 0) + value),
    new Map<number, number>()
  )

const ownerLookup = (etfs: EtfDetailDto[]) => {
  const plain = etfs.filter(etf => etf.constituentSymbols.length === 0)
  const byId = new Map(plain.map(etf => [etf.instrumentId, etf] as const))
  const bySymbol = new Map(
    etfs.flatMap(etf => etf.constituentSymbols.map(symbol => [symbol, etf] as const))
  )
  return (transaction: TransactionResponseDto): EtfDetailDto | undefined =>
    byId.get(transaction.instrumentId) ?? bySymbol.get(transaction.symbol)
}

const toTrade = (transaction: TransactionResponseDto, owner: EtfDetailDto): Trade => ({
  owner: owner.instrumentId,
  cash: owner.symbol === CASH_SYMBOL,
  date: transaction.transactionDate,
  id: transaction.id ?? 0,
  position: `${transaction.instrumentId}:${transaction.platform}`,
  sell: transaction.transactionType === TransactionType.SELL,
  quantity: transaction.quantity,
  amount: transaction.quantity * transaction.price,
})

const toTrades = (etfs: EtfDetailDto[], transactions: TransactionResponseDto[]): Trade[] => {
  const ownerOf = ownerLookup(etfs)
  return transactions
    .flatMap(transaction => {
      const owner = ownerOf(transaction)
      return owner ? [toTrade(transaction, owner)] : []
    })
    .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id)
}

const groupByDate = (trades: Trade[]): Trade[][] => {
  const groups = new Map<string, Trade[]>()
  trades.forEach(trade => groups.set(trade.date, [...(groups.get(trade.date) ?? []), trade]))
  return [...groups.values()]
}

const afterTrade = (held: Position, trade: Trade): Position => {
  if (!trade.sell) {
    return { ...held, quantity: held.quantity + trade.quantity, cost: held.cost + trade.amount }
  }
  if (held.quantity <= 0) return held
  const kept = 1 - Math.min(1, trade.quantity / held.quantity)
  return { ...held, quantity: held.quantity * kept, cost: held.cost * kept }
}

const settle = (positions: Map<string, Position>, trade: Trade): void => {
  const held = positions.get(trade.position) ?? { owner: trade.owner, quantity: 0, cost: 0 }
  positions.set(trade.position, afterTrade(held, trade))
}

const toSnapshot = (positions: Map<string, Position>, trades: Trade[]): Snapshot => {
  trades.forEach(trade => settle(positions, trade))
  return {
    costs: totalsByOwner([...positions.values()].map(held => [held.owner, held.cost])),
    nets: totalsByOwner(
      trades.map(trade => [trade.owner, trade.sell ? -trade.amount : trade.amount])
    ),
  }
}

const printable = (share: number): WeightCell => (share < SMALLEST_SHARE ? null : share)

const investedCell = (costs: Map<number, number>, owner: number): WeightCell => {
  const total = sum(costs.values())
  return total < HALF_CENT ? null : printable(((costs.get(owner) ?? 0) / total) * 100)
}

const boughtCell = (nets: Map<number, number>, owner: number): WeightCell => {
  const net = nets.get(owner) ?? 0
  if (Math.abs(net) < HALF_CENT) return null
  if (net < 0) return 'sold'
  return printable((net / sum([...nets.values()].filter(value => value > 0))) * 100)
}

const toRow = ({ instrumentId, symbol }: EtfDetailDto, snapshots: Snapshot[]): PurchaseRow => ({
  instrumentId,
  symbol,
  invested: snapshots.map(({ costs }) => investedCell(costs, instrumentId)),
  bought: snapshots.map(({ nets }) => boughtCell(nets, instrumentId)),
})

const kindOf = (trades: Trade[]): DayKind => {
  if (trades.every(trade => trade.cash)) return 'cash'
  return trades.some(trade => trade.sell && !trade.cash) ? 'swap' : 'buy'
}

const moneyPart = (trades: Trade[], side: string, sell: boolean): string[] => {
  const amount = sum(trades.filter(trade => trade.sell === sell).map(trade => trade.amount))
  return amount > 0 ? [`${side} ${formatCurrencyWithSymbol(amount)}`] : []
}

const money = (trades: Trade[], kind: DayKind): string => {
  const counted = trades.filter(trade => trade.cash === (kind === 'cash'))
  return [...moneyPart(counted, 'bought', false), ...moneyPart(counted, 'sold', true)].join(', ')
}

const toDay = (trades: Trade[], previous: string | undefined): PurchaseDay => {
  const { date } = trades[0]
  const [year, month, day] = date.split('-').map(Number)
  const kind = kindOf(trades)
  const label = `${day} ${MONTHS[month - 1]} ${year}`
  return {
    date,
    kind,
    day,
    month: MONTHS[month - 1],
    year: previous?.startsWith(`${year}-`) ? null : year,
    label,
    title: [label, KIND_LABELS[kind], money(trades, kind)].filter(Boolean).join(' · '),
  }
}

export const buildPurchaseWeights = (
  etfs: EtfDetailDto[],
  transactions: TransactionResponseDto[]
): PurchaseWeights => {
  const trades = toTrades(etfs, transactions)
  const groups = groupByDate(trades)
  const positions = new Map<string, Position>()
  const snapshots = groups.map(group => toSnapshot(positions, group))
  const traded = new Set(trades.map(trade => trade.owner))
  return {
    days: groups.map((group, index) => toDay(group, groups[index - 1]?.[0].date)),
    rows: etfs.filter(etf => traded.has(etf.instrumentId)).map(etf => toRow(etf, snapshots)),
  }
}

export const dayTargets = (rows: PurchaseRow[], metric: WeightMetric, index: number) => {
  const shown = rows.flatMap(({ instrumentId, [metric]: cells }) => {
    const cell = cells[index]
    return typeof cell === 'number' ? [{ instrumentId, value: Number(cell.toFixed(1)) }] : []
  })
  const largest = shown.reduce((top, target) => (target.value > top.value ? target : top), shown[0])
  const spare = 100 - sum(shown.map(target => target.value))
  return shown.map(target =>
    target === largest ? { ...target, value: Number((target.value + spare).toFixed(1)) } : target
  )
}
