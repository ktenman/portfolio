import { afterAll, beforeAll, describe, it, expect, vi } from 'vitest'
import {
  addDays,
  daysBetween,
  formatDayMonth,
  formatFullDate,
  formatWeekdayDate,
  mondayIndex,
  monthName,
  monthsBefore,
} from './iso-dates'

describe('addDays', () => {
  it.each([
    ['2025-12-31', 1, '2026-01-01'],
    ['2025-03-01', -1, '2025-02-28'],
    ['2024-03-01', -1, '2024-02-29'],
    ['2025-03-29', 2, '2025-03-31'],
    ['2025-10-25', 2, '2025-10-27'],
    ['2025-06-30', 0, '2025-06-30'],
  ] as const)('moves %s by %s days to %s', (date, days, expected) => {
    expect(addDays(date, days)).toBe(expected)
  })
})

describe('daysBetween', () => {
  it.each([
    ['2025-06-30', '2025-12-31', 184],
    ['2025-12-29', '2025-12-31', 2],
    ['2025-03-29', '2025-03-31', 2],
    ['2025-10-25', '2025-10-27', 2],
    ['2025-12-31', '2025-12-30', -1],
    ['2025-12-31', '2025-12-31', 0],
  ] as const)('counts from %s to %s as %s', (from, to, expected) => {
    expect(daysBetween(from, to)).toBe(expected)
  })
})

describe('monthsBefore', () => {
  it.each([
    ['2025-12-31', '2025-06-30'],
    ['2025-08-31', '2025-02-28'],
    ['2024-08-31', '2024-02-29'],
    ['2025-03-15', '2024-09-15'],
    ['2025-06-01', '2024-12-01'],
    ['2025-12-30', '2025-06-30'],
    ['2026-01-01', '2025-07-01'],
  ] as const)('gives six months before %s as %s', (date, expected) => {
    expect(monthsBefore(date, 6)).toBe(expected)
  })
})

describe('mondayIndex', () => {
  it.each([
    ['2025-06-30', 0],
    ['2025-12-31', 2],
    ['2025-12-28', 6],
    ['2025-12-27', 5],
  ] as const)('places %s at index %s', (date, expected) => {
    expect(mondayIndex(date)).toBe(expected)
  })
})

describe('date formats', () => {
  it.each([
    ['2025-12-26', 'Dec', '26 Dec', 'Fri 26 Dec', 'Fri 26 Dec 2025'],
    ['2025-06-30', 'Jun', '30 Jun', 'Mon 30 Jun', 'Mon 30 Jun 2025'],
    ['2025-09-07', 'Sep', '7 Sep', 'Sun 7 Sep', 'Sun 7 Sep 2025'],
    ['2026-01-01', 'Jan', '1 Jan', 'Thu 1 Jan', 'Thu 1 Jan 2026'],
  ] as const)('prints %s', (date, month, dayMonth, weekdayDate, full) => {
    expect([
      monthName(date),
      formatDayMonth(date),
      formatWeekdayDate(date),
      formatFullDate(date),
    ]).toEqual([month, dayMonth, weekdayDate, full])
  })
})

describe('in a time zone west of UTC', () => {
  beforeAll(() => {
    vi.stubEnv('TZ', 'America/Los_Angeles')
  })

  afterAll(() => {
    vi.unstubAllEnvs()
  })

  it('reads every part of a date from its UTC calendar day', () => {
    expect([
      new Date('2026-01-01').getDate(),
      formatFullDate('2026-01-01'),
      formatDayMonth('2025-12-01'),
      mondayIndex('2025-12-01'),
      monthName('2025-12-01'),
      monthsBefore('2026-01-01', 6),
    ]).toEqual([31, 'Thu 1 Jan 2026', '1 Dec', 0, 'Dec', '2025-07-01'])
  })
})
