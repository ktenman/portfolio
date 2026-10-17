import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow as row } from '../tests/fixtures'
import { formatCurrencyChange, formatPercentChange } from '../utils/formatters'
import { addMonths, lastDayOfMonth } from '../utils/iso-dates'
import {
  buildMonthlyProfit,
  formatMonthCell,
  MONTH_FLAT_HINT,
  MONTH_STEPS,
  monthOn,
  monthStepOf,
  percentClass,
  type ObservedMonth,
} from './monthly-profit'

const TODAY = '2030-01-01'
const TAIL = ' · closed at €100,000.00 · XIRR 20.69%'

const build = (rows: PortfolioSummaryDto[]) => buildMonthlyProfit(rows, TODAY)!

const february = (rows: PortfolioSummaryDto[]) => monthOn(build(rows), '2025-02')!

const shown = (month: ObservedMonth) => [
  formatCurrencyChange(month.change),
  formatPercentChange(month.percent!, 1),
  month.step,
]

const monthEnds = (profits: number[]): PortfolioSummaryDto[] =>
  profits.map((profit, index) => row(lastDayOfMonth(addMonths('2024-12', index)), profit))

describe('calculation', () => {
  it('rounds the endpoint difference instead of summing rounded days', () =>
    expect(
      february([
        row('2025-01-31', 0),
        row('2025-02-01', 0.004),
        row('2025-02-02', 0.008),
        row('2025-02-03', 0.012),
      ]).change
    ).toBe(0.01))

  it('keeps opposite euro and percentage signs', () =>
    expect(
      shown(
        february([
          row('2025-01-31', 0, 1000),
          row('2025-02-01', -40, 960),
          row('2025-02-02', 60, 10100),
        ])
      )
    ).toEqual(['+€60.00', '−3.0%', 'loss-3']))

  it('keeps a non-zero percentage when euros net to zero', () =>
    expect(
      shown(
        february([
          row('2025-01-31', 0, 1000),
          row('2025-02-01', 100, 1100),
          row('2025-02-02', 0, 100),
        ])
      )
    ).toEqual(['€0.00', '−45.0%', 'loss-4']))

  it('counts a deposit as value, not profit', () =>
    expect(shown(february([row('2025-01-31', 0, 1000), row('2025-02-01', 10, 2010)]))).toEqual([
      '+€10.00',
      '+0.5%',
      'gain-1',
    ]))

  it('marks the percentage unavailable for zero, negative and non-finite bases', () => {
    const month = (profit: number, value: number) =>
      february([row('2025-01-31', 0), row('2025-02-01', profit, value)])
    const unavailable = [
      null,
      'gain-1',
      'Percentage unavailable',
      'February 2025, to 1 Feb, +€100.00, percentage unavailable, Best of 1 month, ranked by euro profit',
    ]

    expect([
      ...[100, 50, Infinity]
        .map(value => month(100, value))
        .map(gain => [gain.percent, gain.step, gain.note, gain.name]),
      [month(-100, Infinity).step, month(0, Infinity).step],
    ]).toEqual([unavailable, unavailable, unavailable, ['loss-1', 'flat']])
  })
})

describe('colour steps', () => {
  it('colours by the rounded percentage', () => {
    const cases: [number, number | null][] = [
      [1, 0.24],
      [1, 0.29],
      [1, 0.3],
      [1, 0.96],
      [1, 2.46],
      [1, 4.96],
      [1, 150],
      [-1, -0.3],
      [-1, -4.99],
      [1, -3.04],
      [-1, -0.04],
      [0, null],
      [5, null],
      [-5, null],
    ]

    expect(cases.map(([change, percent]) => monthStepOf(change, percent))).toEqual([
      'flat',
      'gain-1',
      'gain-1',
      'gain-2',
      'gain-3',
      'gain-4',
      'gain-4',
      'loss-1',
      'loss-4',
      'loss-3',
      'flat',
      'flat',
      'gain-1',
      'loss-1',
    ])
  })

  it('starts each step at its threshold on both sides', () =>
    expect(MONTH_STEPS.flatMap(step => [monthStepOf(1, step), monthStepOf(-1, -step)])).toEqual([
      'gain-1',
      'loss-1',
      'gain-2',
      'loss-2',
      'gain-3',
      'loss-3',
      'gain-4',
      'loss-4',
    ]))

  it('hints that the flat step stays under the first threshold', () =>
    expect(MONTH_FLAT_HINT).toBe('under ±0.3%'))
})

describe('grid cells', () => {
  it('formats grid cells', () =>
    expect([5.43, -3.04, -0.04, 0, 99.96, 150, -60, null].map(formatMonthCell)).toEqual([
      '+5.4',
      '−3.0',
      '0.0',
      '0.0',
      '+100',
      '+150',
      '−60.0',
      '—',
    ]))

  it('colours a percentage as it prints with one decimal', () =>
    expect([0.04, -0.04, 0.06, -0.06, null].map(percentClass)).toEqual([
      '',
      '',
      'text-gain',
      'text-loss',
      '',
    ]))
})

describe('ranks and statistics', () => {
  it('shares ranks on equal changes and lets the later month win', () => {
    const model = build(monthEnds([0, 100, 200, 250]))

    expect([model.months.map(month => month.detail), model.best?.key]).toEqual([
      [`Best of 3 months${TAIL}`, `Best of 3 months${TAIL}`, `3rd best of 3 months${TAIL}`],
      '2025-02',
    ])
  })

  it('omits best or worst when no month qualifies', () => {
    const losses = build(monthEnds([0, -100, -200]))
    const zeros = build(monthEnds([0, 0, 0]))

    expect([losses.best, zeros.best, zeros.worst, zeros.months.map(month => month.name)]).toEqual([
      null,
      null,
      null,
      ['January 2025, €0.00, 0.0%', 'February 2025, €0.00, 0.0%'],
    ])
  })

  it('uses singular units', () =>
    expect(build(monthEnds([0, 100])).months[0].detail).toBe(`Best of 1 month${TAIL}`))

  it('averages unrounded differences over single months', () =>
    expect(build(monthEnds([0, 0.004, 0.008, 0.02])).averageMonth).toBe(0.01))

  it('shows no average without single months', () => {
    const model = build([row('2025-01-15', 0), row('2025-03-31', 100)])

    expect([model.averageMonth, model.excluded, model.best, model.worst]).toEqual([
      null,
      '1 month not counted, covering more than one month',
      null,
      null,
    ])
  })
})
