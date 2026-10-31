import { describe, it, expect } from 'vitest'
import { TimeRange, type InstrumentDto } from '../models/generated/domain-models'
import { TIME_RANGES } from '../composables/use-time-range'
import { INSTRUMENTS } from '../tests/visual/instruments-fixture'
import {
  HEATMAP_STEPS,
  barShare,
  boxLabel,
  changeOrder,
  drawablePositions,
  squarify,
  tickerOf,
  tierFor,
} from './instrument-heatmap'

const VALUES = [6, 6, 4, 3, 2, 2, 1, 0.5]

const bySymbol = (symbol: string) => INSTRUMENTS.find(instrument => instrument.symbol === symbol)!

const valued = (...values: number[]): InstrumentDto[] =>
  values.map((currentValue, index) => ({ ...INSTRUMENTS[index], currentValue }))

const overlap = (
  a: { x: number; y: number; width: number; height: number },
  b: { x: number; y: number; width: number; height: number }
) =>
  Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x)) *
  Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y))

describe('squarify', () => {
  it('gives each box an area proportional to its value within a pixel', () => {
    const boxes = squarify(
      VALUES.map(value => ({ item: value, value })),
      800,
      600
    )
    const total = VALUES.reduce((sum, value) => sum + value, 0)

    expect(
      boxes.every(box => Math.abs(box.width * box.height - (box.item / total) * 480000) < 1)
    ).toBe(true)
  })

  it('keeps every box inside the map without overlaps', () => {
    const boxes = squarify(
      VALUES.map(value => ({ item: value, value })),
      800,
      600
    )
    const inside = boxes.every(
      box =>
        box.x >= -1e-6 &&
        box.y >= -1e-6 &&
        box.x + box.width <= 800 + 1e-6 &&
        box.y + box.height <= 600 + 1e-6
    )
    const overlaps = boxes.flatMap((a, i) => boxes.slice(i + 1).map(b => overlap(a, b)))

    expect([inside, overlaps.every(area => area < 1e-6)]).toEqual([true, true])
  })

  it('returns the boxes largest value first', () =>
    expect(
      squarify(
        [1, 6, 3].map(value => ({ item: value, value })),
        800,
        600
      ).map(box => box.item)
    ).toEqual([6, 3, 1]))

  it('fills the map with a single position', () =>
    expect(squarify([{ item: 'ä', value: 5 }], 800, 600)).toEqual([
      { item: 'ä', x: 0, y: 0, width: 800, height: 600 },
    ]))

  it('draws nothing without positions', () => expect(squarify([], 800, 600)).toEqual([]))

  it('draws nothing before the map has a size', () =>
    expect(squarify([{ item: 1, value: 1 }], 0, 0)).toEqual([]))
})

describe('instrument heatmap', () => {
  it('has thresholds for every period', () =>
    expect(Object.fromEntries(TIME_RANGES.map(range => [range, HEATMAP_STEPS[range]]))).toEqual({
      '1D': [0.05, 0.3, 0.75, 1.5],
      '2D': [0.1, 0.7, 1.7, 3.4],
      '3D': [0.1, 0.7, 1.7, 3.4],
      '4D': [0.1, 0.7, 1.7, 3.4],
      '5D': [0.1, 0.7, 1.7, 3.4],
      '6D': [0.1, 0.7, 1.7, 3.4],
      '1W': [0.1, 0.7, 1.7, 3.4],
      '2W': [0.1, 0.7, 1.7, 3.4],
      '3W': [0.3, 1, 2.5, 5],
      '4W': [0.3, 1, 2.5, 5],
      '5W': [0.3, 1, 2.5, 5],
      '6W': [0.3, 1, 2.5, 5],
      '1M': [0.3, 1, 2.5, 5],
      '2M': [0.3, 1, 2.5, 5],
      '3M': [0.3, 1, 2.5, 5],
      '4M': [1, 5, 12, 25],
      '5M': [1, 5, 12, 25],
      '6M': [1, 5, 12, 25],
      YTD: [1, 5, 12, 25],
      '1Y': [1, 5, 12, 25],
      '2Y': [2, 10, 25, 50],
      '3Y': [2, 10, 25, 50],
      '4Y': [2, 10, 25, 50],
      '5Y': [2, 10, 25, 50],
      '6Y': [2, 10, 25, 50],
      MAX: [2, 10, 25, 50],
    }))

  it('picks each tier at its exact bounds', () =>
    expect([
      tierFor(210, 186, true),
      tierFor(209, 186, true),
      tierFor(116, 106, true),
      tierFor(70, 72, true),
      tierFor(48, 36, true),
      tierFor(47, 36, true),
      tierFor(500, 500, false),
      tierFor(86, 112, false),
      tierFor(64, 66, false),
      tierFor(44, 34, false),
      tierFor(44, 33, false),
    ]).toEqual(['xl', 'lg', 'lg', 'md', 'sm', 'xs', 'lg', 'lg', 'md', 'sm', 'xs']))

  it('takes the ticker before the exchange', () =>
    expect([tickerOf('TSTINF:GER:EUR'), tickerOf('TSTBTC')]).toEqual(['TSTINF', 'TSTBTC']))

  it('leaves positions under 0.1% out of the map', () =>
    expect(
      [valued(999, 1), valued(999.5, 0.5)].map(list =>
        drawablePositions(list).map(({ currentValue }) => currentValue)
      )
    ).toEqual([[999, 1], [999.5]]))

  it('draws positions largest value first', () =>
    expect(drawablePositions(valued(10, 30, 20)).map(({ currentValue }) => currentValue)).toEqual([
      30, 20, 10,
    ]))

  it('draws nothing when the total value is zero', () =>
    expect(drawablePositions(valued(0, 0))).toEqual([]))

  it('orders the list by change with unknown changes last', () =>
    expect(changeOrder(INSTRUMENTS).map(({ symbol }) => tickerOf(symbol))).toEqual([
      'TSTINF',
      'TSTWLD',
      'TSTDEF',
      'TSTAER',
      'TSTACW',
      'TSTFTS',
      'TSTSMC',
      'TSTBNK',
      'TSTEUR',
      'TSTAIX',
      'TSTEMX',
      'TSTBTC',
      'TSTORS',
      'TSTNDX',
    ]))

  it('sizes bars against the largest change and gives unknown changes none', () =>
    expect([
      barShare(212.66, 212.66),
      barShare(-84.19, 212.66),
      barShare(null, 212.66),
      barShare(0, 0),
    ]).toEqual([1, 84.19 / 212.66, 0, 0]))

  it('labels a box with its figures', () =>
    expect(boxLabel(bySymbol('TSTINF:GER:EUR'), 11.84, TimeRange.ONE_DAY)).toBe(
      'TSTINF, Test S&P 500 Information Technology Sector. Value €18,402.42, 11.84% of the portfolio. 1D change +1.17%, +€212.66.'
    ))

  it('labels a box without a change', () =>
    expect(boxLabel(bySymbol('TSTNDX:NSQ:USD'), 3.89, TimeRange.ONE_WEEK)).toBe(
      'TSTNDX, Test Nasdaq 100 Growth. Value €6,043.31, 3.89% of the portfolio. No 1W change.'
    ))

  it('labels a loss with a minus sign', () =>
    expect(boxLabel(bySymbol('TSTORS:GER:EUR'), 15.97, TimeRange.ONE_DAY)).toBe(
      'TSTORS, Ørsted Green Transition Test Fund. Value €24,811.50, 15.97% of the portfolio. 1D change −0.34%, −€84.19.'
    ))
})
