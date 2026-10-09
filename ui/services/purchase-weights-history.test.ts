import { describe, it, expect } from 'vitest'
import {
  buildPurchaseWeights,
  dayTargets,
  shade,
  type WeightCell,
} from './purchase-weights-history'
import { createTransactionDto } from '../tests/fixtures'
import {
  Platform,
  TransactionType,
  type EtfDetailDto,
  type TransactionResponseDto,
} from '../models/generated/domain-models'

const { BUY, SELL } = TransactionType
const { LHV, TRADING212 } = Platform

type Instrument = readonly [instrumentId: number, symbol: string]
type Trade = [
  date: string,
  instrument: Instrument,
  type: TransactionType,
  quantity: number,
  price: number,
  platform?: Platform,
]

const AIFS: Instrument = [1, 'AIFS:GER:EUR']
const LSMC: Instrument = [3, 'LSMC:GER:EUR']
const VVSM: Instrument = [4, 'VVSM:GER:EUR']
const EUR: Instrument = [14, 'EUR']
const USD: Instrument = [15, 'USD']
const GOOGL: Instrument = [99, 'GOOGL:NSQ:USD']

const etf = (
  instrumentId: number,
  symbol: string,
  constituentSymbols: string[] = []
): EtfDetailDto => ({
  instrumentId,
  symbol,
  name: `Fond ${symbol} õ`,
  allocation: 0,
  ter: null,
  annualReturn: null,
  currentPrice: null,
  fundCurrency: null,
  constituentSymbols,
})

const ETFS = [etf(...AIFS), etf(2, 'CASH', ['EUR', 'USD']), etf(...LSMC), etf(...VVSM)]

const trades = (...rows: Trade[]): TransactionResponseDto[] =>
  rows.map(
    ([transactionDate, instrument, transactionType, quantity, price, platform = LHV], index) =>
      createTransactionDto({
        id: index + 1,
        instrumentId: instrument[0],
        symbol: instrument[1],
        transactionType,
        quantity,
        price,
        transactionDate,
        platform,
      })
  )

const build = (...rows: Trade[]) => buildPurchaseWeights(ETFS, trades(...rows))

const printed = (cells: WeightCell[]) =>
  cells.map(cell => (typeof cell === 'number' ? cell.toFixed(1) : cell))

const PAID_FROM_CASH: Trade[] = [
  ['2026-06-01', EUR, BUY, 1000, 1],
  ['2026-06-16', EUR, SELL, 1000, 1],
  ['2026-06-16', AIFS, BUY, 10, 100],
]

const SWAP: Trade[] = [
  ['2026-06-16', LSMC, BUY, 10, 100],
  ['2026-08-14', LSMC, SELL, 10, 123.146],
  ['2026-08-14', VVSM, BUY, 20, 61.573],
]

const SOLD_AND_BOUGHT: Trade[] = [
  ['2026-06-16', LSMC, BUY, 10, 100],
  ['2026-08-14', LSMC, SELL, 10, 120],
  ['2026-08-14', VVSM, BUY, 9, 100],
  ['2026-08-14', AIFS, BUY, 3, 100],
]

describe('purchase-weights-history', () => {
  it('returns no days and no rows without transactions', () => {
    expect(buildPurchaseWeights(ETFS, [])).toEqual({ days: [], rows: [] })
  })

  it('makes one column per trade day with the oldest on the left', () => {
    const weights = build(
      ['2026-07-16', AIFS, BUY, 10, 100],
      ['2026-06-16', AIFS, BUY, 10, 100],
      ['2026-06-16', LSMC, BUY, 10, 100]
    )
    expect(weights.days.map(day => day.date)).toEqual(['2026-06-16', '2026-07-16'])
  })

  it('gives neither a column nor a row to a transaction that matches no ETF', () => {
    const weights = build(['2026-06-16', AIFS, BUY, 10, 100], ['2026-07-10', GOOGL, BUY, 1, 13.95])
    expect([weights.days.length, weights.rows.map(row => row.symbol)]).toEqual([
      1,
      ['AIFS:GER:EUR'],
    ])
  })

  it('lists the traded ETFs in the order of the ETF list', () => {
    const weights = build(['2026-06-16', VVSM, BUY, 10, 100], ['2026-06-16', AIFS, BUY, 10, 100])
    expect(weights.rows.map(row => row.symbol)).toEqual(['AIFS:GER:EUR', 'VVSM:GER:EUR'])
  })

  it('shows the year on the first column of each year only', () => {
    const weights = build(
      ['2025-12-15', AIFS, BUY, 1, 100],
      ['2026-01-05', AIFS, BUY, 1, 100],
      ['2026-02-16', AIFS, BUY, 1, 100]
    )
    expect(weights.days.map(day => [day.day, day.month, day.year])).toEqual([
      [15, 'Dec', 2025],
      [5, 'Jan', 2026],
      [16, 'Feb', null],
    ])
  })

  it('calls a day of cash trades a cash day and ETF buys paid from cash a buy', () => {
    expect(build(...PAID_FROM_CASH).days.map(day => day.kind)).toEqual(['cash', 'buy'])
  })

  it('calls a day with a sell outside the cash fund a swap', () => {
    expect(build(...SWAP).days[1].kind).toBe('swap')
  })

  it('leaves the cash leg out of the money of a buy day', () => {
    expect(build(...PAID_FROM_CASH).days.map(day => day.title)).toEqual([
      '1 Jun 2026 · Cash · bought €1,000.00',
      '16 Jun 2026 · Buy · bought €1,000.00',
    ])
  })

  it('titles a swap day with the money bought and sold', () => {
    expect(build(...SWAP).days[1].title).toBe(
      '14 Aug 2026 · Swap or rebalance · bought €1,231.46, sold €1,231.46'
    )
  })

  it('titles a day without a buy with the money sold only', () => {
    const weights = build(['2026-06-16', AIFS, BUY, 10, 100], ['2026-07-16', AIFS, SELL, 5, 100])
    expect(weights.days[1].title).toBe('16 Jul 2026 · Swap or rebalance · sold €500.00')
  })

  it('shares the money bought that day among the ETFs bought and marks a sold ETF', () => {
    expect(build(...SOLD_AND_BOUGHT).rows.map(row => printed(row.bought)[1])).toEqual([
      '25.0',
      'sold',
      '75.0',
    ])
  })

  it('leaves the cell of an ETF that was not traded that day empty', () => {
    expect(build(...SOLD_AND_BOUGHT).rows.map(row => printed(row.bought)[0])).toEqual([
      null,
      '100.0',
      null,
    ])
  })

  it('nets the buys and sells of one ETF within the day', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 10, 100],
      ['2026-07-16', AIFS, SELL, 2, 100],
      ['2026-07-16', AIFS, BUY, 5, 100],
      ['2026-07-16', LSMC, BUY, 1, 100]
    )
    expect(weights.rows.map(row => printed(row.bought)[1])).toEqual(['75.0', '25.0'])
  })

  it('removes the sold fraction of the cost on a partial sell', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 15, 100],
      ['2026-06-16', LSMC, BUY, 10, 100],
      ['2026-07-16', LSMC, SELL, 5, 150]
    )
    expect(weights.rows.map(row => printed(row.invested))).toEqual([
      ['60.0', '75.0'],
      ['40.0', '25.0'],
    ])
  })

  it('empties the cells of an ETF sold completely and keeps its row', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 15, 100],
      ['2026-06-16', LSMC, BUY, 10, 100],
      ['2026-08-14', LSMC, SELL, 10, 120]
    )
    expect(weights.rows.map(row => printed(row.invested))).toEqual([
      ['60.0', '100.0'],
      ['40.0', null],
    ])
  })

  it('keeps the cost of one ETF apart per platform', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 10, 100, LHV],
      ['2026-06-16', AIFS, BUY, 10, 300, TRADING212],
      ['2026-06-16', LSMC, BUY, 10, 100, LHV],
      ['2026-07-16', AIFS, SELL, 10, 200, LHV]
    )
    expect(printed(weights.rows[0].invested)).toEqual(['80.0', '75.0'])
  })

  it('adds the constituents of a synthetic fund into its row', () => {
    const weights = build(
      ['2026-10-01', EUR, BUY, 600, 1],
      ['2026-10-01', USD, BUY, 200, 1],
      ['2026-10-01', AIFS, BUY, 2, 100]
    )
    expect(weights.rows.map(row => [row.symbol, printed(row.invested)])).toEqual([
      ['AIFS:GER:EUR', ['20.0']],
      ['CASH', ['80.0']],
    ])
  })

  it('prints nothing for a share below 0.05', () => {
    const weights = build(['2026-06-16', AIFS, BUY, 100, 100], ['2026-06-16', LSMC, BUY, 1, 4.99])
    expect([weights.rows[1].invested, weights.rows[1].bought]).toEqual([[null], [null]])
  })

  it('applies the trades of one day in id order whatever order they arrive in', () => {
    const [buy, sell, other] = trades(
      ['2026-06-16', LSMC, BUY, 10, 100],
      ['2026-06-16', LSMC, SELL, 5, 100],
      ['2026-06-16', AIFS, BUY, 5, 100]
    )
    expect(printed(buildPurchaseWeights(ETFS, [other, sell, buy]).rows[1].invested)).toEqual([
      '50.0',
    ])
  })

  it('never takes the cost of an oversold ETF below zero', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 10, 100],
      ['2026-06-16', LSMC, BUY, 10, 100],
      ['2026-07-16', LSMC, SELL, 12, 100]
    )
    expect(weights.rows.map(row => printed(row.invested)[1])).toEqual(['100.0', null])
  })

  it('ignores a sell on a platform that holds nothing and still shows its day', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 10, 100, LHV],
      ['2026-06-16', LSMC, BUY, 10, 100, LHV],
      ['2026-07-16', AIFS, SELL, 5, 100, TRADING212]
    )
    expect([
      weights.days.map(day => day.kind),
      printed(weights.rows[0].invested),
      weights.rows[0].bought[1],
    ]).toEqual([['buy', 'swap'], ['50.0', '50.0'], 'sold'])
  })

  it('leaves every cell empty once everything is sold', () => {
    const weights = build(['2026-06-16', AIFS, BUY, 10, 100], ['2026-07-16', AIFS, SELL, 10, 100])
    expect(printed(weights.rows[0].invested)).toEqual(['100.0', null])
  })

  it('shows nothing once fractional lots are sold completely', () => {
    const weights = build(
      ['2026-06-16', AIFS, BUY, 0.1, 100],
      ['2026-06-17', AIFS, BUY, 0.2, 100],
      ['2026-07-16', AIFS, SELL, 0.3, 100]
    )
    expect(printed(weights.rows[0].invested)).toEqual(['100.0', '100.0', null])
  })

  it('names each day by its full date', () => {
    expect(build(...SWAP).days.map(day => day.label)).toEqual(['16 Jun 2026', '14 Aug 2026'])
  })

  it('turns the shares shown for a day into targets and skips empty and sold cells', () => {
    const { rows } = build(...SOLD_AND_BOUGHT)
    expect([dayTargets(rows, 'bought', 0), dayTargets(rows, 'bought', 1)]).toEqual([
      [{ instrumentId: 3, value: 100 }],
      [
        { instrumentId: 1, value: 25 },
        { instrumentId: 4, value: 75 },
      ],
    ])
  })

  it('lets the largest target absorb the rounding so the targets total 100', () => {
    const { rows } = build(
      ['2026-06-16', AIFS, BUY, 1, 100],
      ['2026-06-16', LSMC, BUY, 1, 100],
      ['2026-06-16', VVSM, BUY, 4, 100]
    )
    expect(dayTargets(rows, 'invested', 0).map(target => target.value)).toEqual([16.7, 16.7, 66.6])
  })

  it('has no targets for a day that shows no share', () => {
    const { rows } = build(['2026-06-16', AIFS, BUY, 10, 100], ['2026-07-16', AIFS, SELL, 5, 100])
    expect(dayTargets(rows, 'bought', 1)).toEqual([])
  })

  it('shades a share of 40 and above with the darkest tint', () => {
    expect([shade(0), shade(20), shade(40), shade(75)]).toEqual([0, 27.5, 55, 55])
  })
})
