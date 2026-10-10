import { describe, it, expect } from 'vitest'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import {
  createCalendarRow as row,
  createDailyProfitCalendar as build,
  createPortfolioSummaryDto,
} from '../tests/fixtures'
import { addDays } from '../utils/iso-dates'
import {
  buildDailyProfitCalendar,
  dayOn,
  lacksDayBeforeLatest,
  moveDay,
} from './daily-profit-calendar'

const NOT_TODAY = '2030-01-01'
const TAIL = ' · closed at €100,000.00 · XIRR 20.69%'

const rowsFrom = (start: string, profits: number[]): PortfolioSummaryDto[] =>
  profits.map((profit, index) => row(addDays(start, index), profit))

const dayWithChange = (change: number, totalValue = 100000 + change) =>
  build([row('2025-12-30', 1000), row('2025-12-31', 1000 + change, totalValue)]).days[1]

describe('period', () => {
  it('returns null with fewer than two rows in the period', () => {
    expect([
      buildDailyProfitCalendar([], NOT_TODAY),
      buildDailyProfitCalendar([row('2025-12-31', 1000)], NOT_TODAY),
      buildDailyProfitCalendar([row('2025-06-29', 900), row('2025-12-31', 1000)], NOT_TODAY),
    ]).toEqual([null, null, null])
  })

  it('accepts rows newest first', () => {
    const rows = rowsFrom('2025-12-01', [1000, 1100, 1050])

    const calendar = build([...rows].reverse())

    expect(calendar.days.map(day => [day.date, day.change])).toEqual([
      ['2025-12-01', null],
      ['2025-12-02', 100],
      ['2025-12-03', -50],
    ])
  })

  it('starts six months before the latest day and drops earlier rows', () => {
    const rows = [...rowsFrom('2025-06-27', [700, 800, 900, 1000, 1100]), row('2025-12-31', 2000)]

    const calendar = build(rows)

    expect([calendar.days[0].date, calendar.days[0].kind, calendar.days.length]).toEqual([
      '2025-06-30',
      'starting',
      185,
    ])
  })

  it('starts on the first row on or after the earliest possible start', () => {
    const rows = [row('2025-06-28', 900), row('2025-07-02', 1000), row('2025-12-31', 2000)]

    expect(build(rows).days[0].date).toBe('2025-07-02')
  })

  it('places a period that starts mid-week', () => {
    const calendar = build(rowsFrom('2025-12-10', Array(14).fill(1000)))

    expect([calendar.offset, calendar.weeks]).toEqual([2, 3])
  })
})

describe('change, percentage and step', () => {
  it.each([
    [0, 'flat'],
    [44, 'flat'],
    [49.7, 'gain-1'],
    [50, 'gain-1'],
    [244, 'gain-1'],
    [250, 'gain-2'],
    [500, 'gain-3'],
    [994, 'gain-3'],
    [1000, 'gain-4'],
    [-44, 'flat'],
    [-49.7, 'loss-1'],
    [-50, 'loss-1'],
    [-250, 'loss-2'],
    [-500, 'loss-3'],
    [-1000, 'loss-4'],
  ] as const)('puts a change of %s on a base of 100,000 in step %s', (change, step) => {
    expect(dayWithChange(change).step).toBe(step)
  })

  it('takes the percentage from the total value before the change', () => {
    const day = dayWithChange(500, 100500)

    expect(day.percent).toBeCloseTo(0.5, 10)
    expect(day.name).toBe('Wed 31 Dec 2025, +€500.00, +0.50%')
  })

  it('rounds the change to whole cents before anything is derived from it', () => {
    const calendar = build([row('2025-12-30', 50.07), row('2025-12-31', 100.07, 100050)])

    expect(calendar.days[1].change).toBe(50)
    expect(calendar.days[1].name).toBe('Wed 31 Dec 2025, +€50.00, +0.05%')
  })

  it('never keeps a negative zero', () => {
    const day = dayWithChange(-0.001)

    expect(day.change).toBe(0)
    expect([day.step, day.name]).toEqual(['flat', 'Wed 31 Dec 2025, €0.00, 0.00%'])
  })

  it('prints a small loss as 0.00% without a sign and keeps it flat', () => {
    const calendar = build([row('2025-07-23', 5000), row('2025-07-24', 4997.29, 111520.38)])

    expect([calendar.days[1].step, calendar.days[1].name]).toEqual([
      'flat',
      'Thu 24 Jul 2025, −€2.71, 0.00%',
    ])
  })

  it('leaves the percentage out when the value before the change is not positive', () => {
    const gain = dayWithChange(500, 500)
    const unchanged = dayWithChange(0, 0)

    expect([gain.percent, gain.step, gain.name]).toEqual([
      null,
      'gain-1',
      'Wed 31 Dec 2025, +€500.00',
    ])
    expect([unchanged.percent, unchanged.step, unchanged.name]).toEqual([
      null,
      'flat',
      'Wed 31 Dec 2025, €0.00',
    ])
  })
})

describe('missing days', () => {
  it('marks a date without a row and counts the next day without ranking it', () => {
    const calendar = build([
      row('2025-12-28', 1000),
      row('2025-12-29', 1100),
      row('2025-12-31', 1300),
    ])

    expect(calendar.days.map(day => day.kind)).toEqual([
      'starting',
      'counted',
      'missing',
      'counted',
    ])
    expect(dayOn(calendar, '2025-12-30')).toEqual({
      date: '2025-12-30',
      kind: 'missing',
      step: null,
      change: null,
      percent: null,
      name: 'Tue 30 Dec 2025, no daily summary',
      closing: '',
      detail: '',
      note: '',
    })
    expect(dayOn(calendar, '2025-12-31')).toMatchObject({
      change: 200,
      step: 'gain-1',
      detail: 'Closed at €100,000.00 · XIRR 20.69%',
      note: 'Covers 2 days, no daily summary for 30 Dec',
    })
    expect(dayOn(calendar, '2025-12-29')?.detail).toBe(`Best of 1 single day${TAIL}`)
  })

  it.each([
    ['2025-12-29', '2025-12-31', 'Covers 2 days, no daily summary for 30 Dec'],
    ['2025-12-27', '2025-12-31', 'Covers 4 days, no daily summary for 28 to 30 Dec'],
    ['2025-11-29', '2025-12-03', 'Covers 4 days, no daily summary for 30 Nov to 2 Dec'],
    ['2025-12-29', '2026-01-02', 'Covers 4 days, no daily summary for 30 Dec to 1 Jan'],
  ] as const)('notes the gap between %s and %s', (previous, date, note) => {
    const calendar = build([row(previous, 1000), row(date, 1100)])

    expect(calendar.days[calendar.days.length - 1].note).toBe(note)
  })

  it('has no best or worst day when no day follows the day before it', () => {
    const calendar = build([row('2025-12-29', 1000), row('2025-12-31', 1300)])

    expect([calendar.best, calendar.worst, calendar.upDays]).toEqual([null, null, 1])
  })
})

describe('figures', () => {
  it('counts up, down and flat days by the step of their cells', () => {
    const rows = [
      ...rowsFrom('2025-12-01', [1000, 1100, 1000, 1010, 1000, 1000, 2000]),
      row('2025-12-09', 2500),
    ]

    const calendar = build(rows)

    expect([calendar.upDays, calendar.downDays, calendar.flatDays]).toEqual([3, 1, 3])
  })

  it('averages the change in total profit over the counted days', () => {
    const consecutive = build(rowsFrom('2025-12-01', [1000, 1001, 1002, 1003.34]))
    const withGap = build([
      row('2025-12-01', 1000),
      row('2025-12-02', 1100),
      row('2025-12-05', 1300),
    ])

    expect([consecutive.averageDay, withGap.averageDay]).toEqual([1.11, 150])
  })

  it('picks the largest gain and loss among single days, the latest of tied days', () => {
    const rows = [
      ...rowsFrom('2025-12-01', [1000, 1200, 1100, 1300, 1200, 1250]),
      row('2025-12-08', 9000),
    ]

    const calendar = build(rows)

    expect([calendar.best?.date, calendar.worst?.date]).toEqual(['2025-12-04', '2025-12-05'])
  })

  it('has no best day in a period with no gain and no worst day in one with no loss', () => {
    const falling = build(rowsFrom('2025-12-01', [1000, 900, 900, 850]))
    const rising = build(rowsFrom('2025-12-01', [1000, 1100, 1100]))

    expect([falling.best, falling.worst?.date, falling.upDays]).toEqual([null, '2025-12-02', 0])
    expect([rising.best?.date, rising.worst]).toEqual(['2025-12-02', null])
  })
})

describe('rank and detail', () => {
  it('ranks single days by amount and lets equal changes share a rank', () => {
    const calendar = build(rowsFrom('2025-12-01', [1000, 1200, 1100, 1300, 1200, 1250]))

    expect(calendar.days.slice(1).map(day => day.detail)).toEqual([
      `Best of 5 single days${TAIL}`,
      `Worst of 5 single days${TAIL}`,
      `Best of 5 single days${TAIL}`,
      `Worst of 5 single days${TAIL}`,
      `3rd best of 5 single days${TAIL}`,
    ])
  })

  it('spells every ordinal', () => {
    const profits = Array.from({ length: 24 }, (_, index) => 1000 - (index * (index - 47)) / 2)

    const ranks = build(rowsFrom('2025-12-01', profits))
      .days.slice(1)
      .map(day => day.detail.split(' of ')[0])

    expect(ranks).toEqual([
      'Best',
      '2nd best',
      '3rd best',
      '4th best',
      '5th best',
      '6th best',
      '7th best',
      '8th best',
      '9th best',
      '10th best',
      '11th best',
      '12th best',
      '13th best',
      '14th best',
      '15th best',
      '16th best',
      '17th best',
      '18th best',
      '19th best',
      '20th best',
      '21st best',
      '22nd best',
      '23rd best',
    ])
  })

  it('starts the line with Closed at for a change of zero', () => {
    expect(dayWithChange(0).detail).toBe('Closed at €100,000.00 · XIRR 20.69%')
  })

  it('describes the starting day without a change, a percentage or a rank', () => {
    const start = createPortfolioSummaryDto({
      date: '2025-06-30',
      totalProfit: 1000,
      totalValue: 103031.33,
      xirrAnnualReturn: 0.1068,
    })

    expect(build([start, row('2025-07-01', 1100)]).days[0]).toEqual({
      date: '2025-06-30',
      kind: 'starting',
      step: null,
      change: null,
      percent: null,
      name: 'Mon 30 Jun 2025, starting day',
      closing: 'Closed at €103,031.33',
      detail: 'Closed at €103,031.33 · XIRR 10.68%',
      note: 'Starting day, not counted or ranked',
    })
  })

  it('names a day by its date, change and percentage as the panel prints them', () => {
    const calendar = build([row('2025-12-25', 5000), row('2025-12-26', 6012.89, 174396.35)])

    expect(calendar.days[1]).toMatchObject({
      name: 'Fri 26 Dec 2025, +€1,012.89, +0.58%',
      closing: 'Closed at €174,396.35',
      detail: 'Best of 1 single day · closed at €174,396.35 · XIRR 20.69%',
      note: '',
    })
  })

  it('prints a negative XIRR with the minus sign of the change', () => {
    const latest = createPortfolioSummaryDto({
      date: '2025-12-31',
      totalProfit: -312.4,
      totalValue: 9812.4,
      xirrAnnualReturn: -0.0325,
    })

    expect(build([row('2025-12-30', -300, 9824.8), latest]).days[1]).toMatchObject({
      name: 'Wed 31 Dec 2025, −€12.40, −0.13%',
      detail: 'Worst of 1 single day · closed at €9,812.40 · XIRR −3.25%',
    })
  })

  it('says Now at only on the latest day while it is today', () => {
    const rows = rowsFrom('2025-12-01', [1000, 1100, 1300])

    const latestIsToday = build(rows, '2025-12-03')
    const earlierIsToday = build(rows, '2025-12-02')

    expect(latestIsToday.days.map(day => day.closing)).toEqual([
      'Closed at €100,000.00',
      'Closed at €100,000.00',
      'Now at €100,000.00',
    ])
    expect(latestIsToday.days[2].detail).toBe(
      'Best of 2 single days · now at €100,000.00 · XIRR 20.69%'
    )
    expect(earlierIsToday.days.map(day => day.closing)).toEqual(
      Array(3).fill('Closed at €100,000.00')
    )
  })
})

describe('month names', () => {
  it.each([
    [
      'above the column of each 1st',
      '2025-06-30',
      '2025-12-31',
      [
        { column: 0, label: 'Jul' },
        { column: 4, label: 'Aug' },
        { column: 9, label: 'Sep' },
        { column: 13, label: 'Oct' },
        { column: 17, label: 'Nov' },
        { column: 22, label: 'Dec' },
      ],
    ],
    [
      'above the first column when the next name is three columns away',
      '2025-12-08',
      '2026-01-05',
      [
        { column: 0, label: 'Dec' },
        { column: 3, label: 'Jan' },
      ],
    ],
    [
      'not above the first column when the next name is closer',
      '2025-12-15',
      '2026-01-05',
      [{ column: 2, label: 'Jan' }],
    ],
    [
      'once for a period inside one month',
      '2025-12-10',
      '2025-12-20',
      [{ column: 0, label: 'Dec' }],
    ],
    [
      'once for a period that starts on the 1st',
      '2025-12-01',
      '2025-12-20',
      [{ column: 0, label: 'Dec' }],
    ],
    [
      'above the column of its 1st when the period starts on a Friday',
      '2025-12-12',
      '2026-01-05',
      [
        { column: 0, label: 'Dec' },
        { column: 3, label: 'Jan' },
      ],
    ],
  ] as const)('names a month %s', (_, first, last, months) => {
    expect(build([row(first, 1000), row(last, 1100)]).months).toEqual(months)
  })
})

describe('moveDay and dayOn', () => {
  const calendar = build([
    row('2025-12-20', 1000),
    row('2025-12-21', 1000),
    ...rowsFrom('2025-12-23', Array(7).fill(1000)),
    row('2025-12-31', 1000),
  ])

  it.each([
    ['2025-12-24', 1, '2025-12-25'],
    ['2025-12-29', 1, '2025-12-31'],
    ['2025-12-31', -1, '2025-12-29'],
    ['2025-12-23', 7, '2025-12-31'],
    ['2025-12-29', -7, '2025-12-21'],
    ['2025-12-28', -7, '2025-12-21'],
    ['2025-12-31', 1, null],
    ['2025-12-20', -1, null],
    ['2025-12-26', 7, null],
    ['2025-12-25', -7, null],
  ] as const)('moves from %s by %s to %s', (date, step, expected) => {
    expect(moveDay(calendar, date, step)).toBe(expected)
  })

  it('steps over a gap of two days in both directions', () => {
    const gapped = build([
      ...rowsFrom('2025-12-01', [1000, 1000]),
      ...rowsFrom('2025-12-05', [1000, 1000]),
    ])

    expect([moveDay(gapped, '2025-12-02', 1), moveDay(gapped, '2025-12-05', -1)]).toEqual([
      '2025-12-05',
      '2025-12-02',
    ])
  })

  it('finds a day by its date and nothing outside the period', () => {
    expect([
      dayOn(calendar, '2025-12-23')?.date,
      dayOn(calendar, '2025-12-19'),
      dayOn(calendar, '2026-01-01'),
    ]).toEqual(['2025-12-23', undefined, undefined])
  })
})

describe('lacksDayBeforeLatest', () => {
  it.each([
    ['no rows', [], false],
    ['one row', ['2025-12-31'], true],
    ['a gap before the latest day', ['2025-12-29', '2025-12-31'], true],
    ['the day before the latest day', ['2025-12-30', '2025-12-31'], false],
    ['rows newest first', ['2025-12-31', '2025-12-30'], false],
  ] as const)('with %s', (_, dates, expected) => {
    expect(lacksDayBeforeLatest(dates.map(date => row(date, 1000)))).toBe(expected)
  })
})
