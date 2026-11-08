import { describe, expect, it } from 'vitest'
import {
  chipDate,
  clampPeriod,
  describePeriod,
  longDate,
  moveEdge,
  periodChange,
  periodDays,
  periodLabel,
  slidePeriod,
  spanPeriod,
  stepEdge,
  type PeriodEdge,
} from './chart-period'

const TODAY = '2026-11-08'
const BOUNDS = { from: '2025-11-08', to: TODAY }
const SUMMER = { from: '2026-06-24', to: '2026-09-10' }

const day = (date: string, totalValue: number, totalProfit: number) => ({
  date,
  totalValue,
  totalProfit,
})

describe('describePeriod', () => {
  it('should name both days of a period inside the current year', () => {
    expect(describePeriod(SUMMER, TODAY)).toBe('from 24 June to 10 September')
  })

  it('should read since the first day when the period ends on the last day', () => {
    expect(describePeriod({ from: '2026-06-24', to: TODAY }, TODAY)).toBe('since 24 June')
  })

  it('should add the year to a since phrase that starts in an earlier year', () => {
    expect(describePeriod({ from: '2025-03-03', to: TODAY }, TODAY)).toBe('since 3 March 2025')
  })

  it('should name a whole calendar year by its number', () => {
    expect(describePeriod({ from: '2025-01-01', to: '2025-12-31' }, TODAY)).toBe('in 2025')
  })

  it('should add the year once to a period inside an earlier year', () => {
    expect(describePeriod({ from: '2025-03-03', to: '2025-09-10' }, TODAY)).toBe(
      'from 3 March to 10 September 2025'
    )
  })

  it('should add both years to a period that crosses a new year', () => {
    expect(describePeriod({ from: '2024-03-03', to: '2025-09-10' }, TODAY)).toBe(
      'from 3 March 2024 to 10 September 2025'
    )
  })
})

describe('periodLabel', () => {
  it('should shorten a period inside the current year to its days', () => {
    expect(periodLabel(SUMMER, TODAY)).toBe('24 Jun – 10 Sep')
  })

  it('should add the year once to a period inside an earlier year', () => {
    expect(periodLabel({ from: '2025-03-03', to: '2025-09-10' }, TODAY)).toBe('3 Mar – 10 Sep 2025')
  })

  it('should add both years to a period that crosses a new year', () => {
    expect(periodLabel({ from: '2024-03-03', to: '2025-09-10' }, TODAY)).toBe(
      '3 Mar 2024 – 10 Sep 2025'
    )
  })

  it('should name a whole calendar year by its number', () => {
    expect(periodLabel({ from: '2025-01-01', to: '2025-12-31' }, TODAY)).toBe('2025')
  })
})

describe('chipDate', () => {
  it('should leave the year off a day of the current year', () => {
    expect(chipDate('2026-06-24', TODAY)).toBe('24 Jun')
  })

  it('should add the year to a day of an earlier year', () => {
    expect(chipDate('2025-06-24', TODAY)).toBe('24 Jun 2025')
  })
})

describe('longDate', () => {
  it('should spell out the month and keep the year', () => {
    expect(longDate('2026-06-24')).toBe('24 June 2026')
  })
})

describe('periodDays', () => {
  it('should count the days from the first day to the last', () => {
    expect(periodDays(SUMMER)).toBe(78)
  })
})

describe('periodChange', () => {
  const days = [
    day('2026-01-01', 1000, 100),
    day('2026-01-02', 1600, 150),
    day('2026-01-03', 1500, 250),
  ]

  it('should measure the change as the profit made between the two days', () => {
    expect(periodChange(days, { from: '2026-01-01', to: '2026-01-03' }).amount).toBe(150)
  })

  it('should add only each day of net buying to the capital', () => {
    expect(periodChange(days, { from: '2026-01-01', to: '2026-01-03' }).percent).toBeCloseTo(
      (150 / 1550) * 100,
      10
    )
  })

  it('should read a day with no summary from the day before it', () => {
    const gap = [day('2026-01-01', 1000, 100), day('2026-01-03', 1100, 200)]

    expect(periodChange(gap, { from: '2026-01-02', to: '2026-01-03' })).toEqual({
      amount: 100,
      percent: 10,
    })
  })

  it('should give no percentage when the period starts without capital', () => {
    const empty = [day('2026-01-01', 0, 0), day('2026-01-02', 10, 10)]

    expect(periodChange(empty, { from: '2026-01-01', to: '2026-01-02' }).percent).toBe(0)
  })
})

describe('moveEdge', () => {
  it('should stop the first day two days before the last day', () => {
    expect(moveEdge(SUMMER, 'from', '2026-09-09', BOUNDS)).toEqual({
      from: '2026-09-08',
      to: '2026-09-10',
    })
  })

  it('should stop the first day at the start of the chart', () => {
    expect(moveEdge(SUMMER, 'from', '2025-01-01', BOUNDS).from).toBe('2025-11-08')
  })

  it('should stop the last day two days after the first day', () => {
    expect(moveEdge(SUMMER, 'to', '2026-06-25', BOUNDS)).toEqual({
      from: '2026-06-24',
      to: '2026-06-26',
    })
  })

  it('should stop the last day at the end of the chart', () => {
    expect(moveEdge(SUMMER, 'to', '2027-01-01', BOUNDS).to).toBe(TODAY)
  })
})

describe('slidePeriod', () => {
  it('should move both days and keep the length', () => {
    expect(slidePeriod(SUMMER, 30, BOUNDS)).toEqual({ from: '2026-07-24', to: '2026-10-10' })
  })

  it('should stop sliding at the end of the chart', () => {
    expect(slidePeriod(SUMMER, 200, BOUNDS)).toEqual({ from: '2026-08-22', to: TODAY })
  })

  it('should stop sliding at the start of the chart', () => {
    expect(slidePeriod(SUMMER, -400, BOUNDS)).toEqual({ from: '2025-11-08', to: '2026-01-25' })
  })
})

describe('stepEdge', () => {
  it.each<[string, boolean, PeriodEdge, string]>([
    ['ArrowLeft', false, 'from', '2026-06-23'],
    ['ArrowDown', false, 'from', '2026-06-23'],
    ['ArrowRight', false, 'to', '2026-09-11'],
    ['ArrowUp', false, 'to', '2026-09-11'],
    ['ArrowRight', true, 'from', '2026-07-24'],
    ['PageDown', false, 'to', '2026-08-11'],
    ['PageUp', false, 'from', '2026-07-24'],
    ['Home', false, 'from', '2025-11-08'],
    ['End', false, 'to', TODAY],
  ])('should move the edge for %s with shift %s', (key, shift, edge, expected) => {
    expect(stepEdge(SUMMER, edge, key, shift, BOUNDS)?.[edge]).toBe(expected)
  })

  it('should ignore a key that does not move an edge', () => {
    expect(stepEdge(SUMMER, 'from', 'a', false, BOUNDS)).toBeNull()
  })
})

describe('spanPeriod', () => {
  it('should put the earlier day first whichever way the drag went', () => {
    expect(spanPeriod('2026-09-10', '2026-06-24')).toEqual(SUMMER)
  })
})

describe('clampPeriod', () => {
  it('should pull a period back inside the chart', () => {
    expect(clampPeriod({ from: '2025-01-01', to: '2026-06-24' }, BOUNDS)).toEqual({
      from: '2025-11-08',
      to: '2026-06-24',
    })
  })

  it('should drop a period with less than two days left inside the chart', () => {
    expect(clampPeriod({ from: '2025-10-01', to: '2025-11-09' }, BOUNDS)).toBeNull()
  })
})
