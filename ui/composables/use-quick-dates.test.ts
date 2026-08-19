import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  formatDateToString,
  calculateDateRange,
  getLabelForPreset,
  useQuickDates,
  QUICK_DATE_OPTIONS,
} from './use-quick-dates'

const quickDates = (onDateSet?: () => void) =>
  useQuickDates({
    fromDateKey: 'test_from',
    untilDateKey: 'test_until',
    selectedQuickDateKey: 'test_selected',
    onDateSet,
  })

describe('use-quick-dates', () => {
  describe('formatDateToString', () => {
    it.each([
      [new Date(2024, 5, 15), '2024-06-15'],
      [new Date(2024, 0, 15), '2024-01-15'],
      [new Date(2024, 11, 5), '2024-12-05'],
      [new Date(2025, 0, 1), '2025-01-01'],
    ] as const)('formats %s as %s', (date, expected) => {
      expect(formatDateToString(date)).toBe(expected)
    })
  })

  describe('calculateDateRange', () => {
    afterEach(() => {
      vi.useRealTimers()
    })

    it.each([
      ['today', new Date(2024, 5, 15), '2024-06-15', '2024-06-15'],
      ['last7Days', new Date(2024, 5, 15), '2024-06-09', '2024-06-15'],
      ['thisWeek', new Date(2024, 5, 15), '2024-06-10', '2024-06-16'],
      ['thisWeek', new Date(2024, 5, 16), '2024-06-10', '2024-06-16'],
      ['thisWeek', new Date(2024, 5, 10), '2024-06-10', '2024-06-16'],
      ['lastWeek', new Date(2024, 5, 15), '2024-06-03', '2024-06-09'],
      ['lastWeek', new Date(2024, 6, 3), '2024-06-24', '2024-06-30'],
      ['last30Days', new Date(2024, 5, 15), '2024-05-17', '2024-06-15'],
      ['thisMonth', new Date(2024, 5, 15), '2024-06-01', '2024-06-30'],
      ['thisMonth', new Date(2024, 1, 15), '2024-02-01', '2024-02-29'],
      ['thisMonth', new Date(2024, 0, 15), '2024-01-01', '2024-01-31'],
      ['lastMonth', new Date(2024, 5, 15), '2024-05-01', '2024-05-31'],
      ['lastMonth', new Date(2024, 0, 15), '2023-12-01', '2023-12-31'],
      ['lastMonth', new Date(2024, 2, 15), '2024-02-01', '2024-02-29'],
      ['thisYear', new Date(2024, 5, 15), '2024-01-01', '2024-12-31'],
      ['lastYear', new Date(2024, 5, 15), '2023-01-01', '2023-12-31'],
    ] as const)('resolves %s on %s to the range %s through %s', (preset, now, from, until) => {
      vi.useFakeTimers()
      vi.setSystemTime(now)

      const range = calculateDateRange(preset)

      expect([formatDateToString(range.from), formatDateToString(range.until)]).toEqual([
        from,
        until,
      ])
    })
  })

  describe('getLabelForPreset', () => {
    it.each([
      ['today', 'Today'],
      ['last7Days', 'Last 7 Days'],
      ['thisWeek', 'This Week'],
      ['lastWeek', 'Last Week'],
      ['last30Days', 'Last 30 Days'],
      ['thisMonth', 'This Month'],
      ['lastMonth', 'Last Month'],
      ['thisYear', 'This Year'],
      ['lastYear', 'Last Year'],
    ] as const)('labels %s as %s', (preset, expected) => {
      expect(getLabelForPreset(preset)).toBe(expected)
    })
  })

  describe('QUICK_DATE_OPTIONS', () => {
    it('should offer one option per preset', () => {
      expect(QUICK_DATE_OPTIONS).toHaveLength(9)
    })

    it('should pair a preset with a label in every option', () => {
      expect(
        QUICK_DATE_OPTIONS.every(
          option => typeof option.preset === 'string' && typeof option.label === 'string'
        )
      ).toBe(true)
    })
  })

  describe('useQuickDates', () => {
    beforeEach(() => {
      localStorage.clear()
      vi.useFakeTimers()
      vi.setSystemTime(new Date(2024, 5, 15))
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('should initialize with empty values', () => {
      const { fromDate, untilDate, selectedQuickDate } = quickDates()

      expect([fromDate.value, untilDate.value, selectedQuickDate.value]).toEqual(['', '', ''])
    })

    it.each([
      ['today', '2024-06-15', '2024-06-15', 'Today'],
      ['last30Days', '2024-05-17', '2024-06-15', 'Last 30 Days'],
      ['thisYear', '2024-01-01', '2024-12-31', 'This Year'],
      ['lastYear', '2023-01-01', '2023-12-31', 'Last Year'],
    ] as const)('sets %s to the range %s through %s labelled %s', (preset, from, until, label) => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate } = quickDates()

      setQuickDate(preset)

      expect([fromDate.value, untilDate.value, selectedQuickDate.value]).toEqual([
        from,
        until,
        label,
      ])
    })

    it('should call onDateSet callback when dates are set', () => {
      const onDateSet = vi.fn()
      const { setQuickDate } = quickDates(onDateSet)

      setQuickDate('last7Days')

      expect(onDateSet).toHaveBeenCalledOnce()
    })

    it('should clear all dates when clearDates is called', () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate, clearDates } = quickDates()

      setQuickDate('thisMonth')
      clearDates()

      expect([fromDate.value, untilDate.value, selectedQuickDate.value]).toEqual(['', '', ''])
    })
  })
})
