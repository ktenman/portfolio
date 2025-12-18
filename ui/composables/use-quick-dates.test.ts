import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  formatDateToString,
  calculateDateRange,
  getLabelForPreset,
  useQuickDates,
  QUICK_DATE_OPTIONS,
  type QuickDatePreset,
} from './use-quick-dates'

describe('use-quick-dates', () => {
  describe('formatDateToString', () => {
    it('should format date as YYYY-MM-DD', () => {
      const date = new Date(2024, 5, 15)
      expect(formatDateToString(date)).toBe('2024-06-15')
    })

    it('should pad single digit months', () => {
      const date = new Date(2024, 0, 15)
      expect(formatDateToString(date)).toBe('2024-01-15')
    })

    it('should pad single digit days', () => {
      const date = new Date(2024, 11, 5)
      expect(formatDateToString(date)).toBe('2024-12-05')
    })

    it('should handle year boundary', () => {
      const date = new Date(2025, 0, 1)
      expect(formatDateToString(date)).toBe('2025-01-01')
    })
  })

  describe('calculateDateRange', () => {
    let mockDate: Date

    beforeEach(() => {
      mockDate = new Date(2024, 5, 15)
      vi.useFakeTimers()
      vi.setSystemTime(mockDate)
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    describe('today', () => {
      it('should return same date for from and until', () => {
        const range = calculateDateRange('today')
        expect(formatDateToString(range.from)).toBe('2024-06-15')
        expect(formatDateToString(range.until)).toBe('2024-06-15')
      })
    })

    describe('last7Days', () => {
      it('should return 6 days ago to today', () => {
        const range = calculateDateRange('last7Days')
        expect(formatDateToString(range.from)).toBe('2024-06-09')
        expect(formatDateToString(range.until)).toBe('2024-06-15')
      })
    })

    describe('thisWeek', () => {
      it('should return Monday to Sunday of current week', () => {
        const range = calculateDateRange('thisWeek')
        expect(formatDateToString(range.from)).toBe('2024-06-10')
        expect(formatDateToString(range.until)).toBe('2024-06-16')
      })

      it('should handle Sunday correctly', () => {
        vi.setSystemTime(new Date(2024, 5, 16))
        const range = calculateDateRange('thisWeek')
        expect(formatDateToString(range.from)).toBe('2024-06-10')
        expect(formatDateToString(range.until)).toBe('2024-06-16')
      })

      it('should handle Monday correctly', () => {
        vi.setSystemTime(new Date(2024, 5, 10))
        const range = calculateDateRange('thisWeek')
        expect(formatDateToString(range.from)).toBe('2024-06-10')
        expect(formatDateToString(range.until)).toBe('2024-06-16')
      })
    })

    describe('lastWeek', () => {
      it('should return Monday to Sunday of previous week', () => {
        const range = calculateDateRange('lastWeek')
        expect(formatDateToString(range.from)).toBe('2024-06-03')
        expect(formatDateToString(range.until)).toBe('2024-06-09')
      })

      it('should handle week crossing month boundary', () => {
        vi.setSystemTime(new Date(2024, 6, 3))
        const range = calculateDateRange('lastWeek')
        expect(formatDateToString(range.from)).toBe('2024-06-24')
        expect(formatDateToString(range.until)).toBe('2024-06-30')
      })
    })

    describe('last30Days', () => {
      it('should return 29 days ago to today', () => {
        const range = calculateDateRange('last30Days')
        expect(formatDateToString(range.from)).toBe('2024-05-17')
        expect(formatDateToString(range.until)).toBe('2024-06-15')
      })
    })

    describe('thisMonth', () => {
      it('should return first day to last day of current month', () => {
        const range = calculateDateRange('thisMonth')
        expect(formatDateToString(range.from)).toBe('2024-06-01')
        expect(formatDateToString(range.until)).toBe('2024-06-30')
      })

      it('should handle February correctly', () => {
        vi.setSystemTime(new Date(2024, 1, 15))
        const range = calculateDateRange('thisMonth')
        expect(formatDateToString(range.from)).toBe('2024-02-01')
        expect(formatDateToString(range.until)).toBe('2024-02-29')
      })

      it('should handle 31-day month', () => {
        vi.setSystemTime(new Date(2024, 0, 15))
        const range = calculateDateRange('thisMonth')
        expect(formatDateToString(range.from)).toBe('2024-01-01')
        expect(formatDateToString(range.until)).toBe('2024-01-31')
      })
    })

    describe('lastMonth', () => {
      it('should return first day to last day of previous month', () => {
        const range = calculateDateRange('lastMonth')
        expect(formatDateToString(range.from)).toBe('2024-05-01')
        expect(formatDateToString(range.until)).toBe('2024-05-31')
      })

      it('should handle January correctly going to December', () => {
        vi.setSystemTime(new Date(2024, 0, 15))
        const range = calculateDateRange('lastMonth')
        expect(formatDateToString(range.from)).toBe('2023-12-01')
        expect(formatDateToString(range.until)).toBe('2023-12-31')
      })

      it('should handle March to February transition in leap year', () => {
        vi.setSystemTime(new Date(2024, 2, 15))
        const range = calculateDateRange('lastMonth')
        expect(formatDateToString(range.from)).toBe('2024-02-01')
        expect(formatDateToString(range.until)).toBe('2024-02-29')
      })
    })

    describe('thisYear', () => {
      it('should return Jan 1 to Dec 31 of current year', () => {
        const range = calculateDateRange('thisYear')
        expect(formatDateToString(range.from)).toBe('2024-01-01')
        expect(formatDateToString(range.until)).toBe('2024-12-31')
      })
    })

    describe('lastYear', () => {
      it('should return Jan 1 to Dec 31 of previous year', () => {
        const range = calculateDateRange('lastYear')
        expect(formatDateToString(range.from)).toBe('2023-01-01')
        expect(formatDateToString(range.until)).toBe('2023-12-31')
      })
    })
  })

  describe('getLabelForPreset', () => {
    it('should return correct label for each preset', () => {
      const presets: QuickDatePreset[] = [
        'today',
        'last7Days',
        'thisWeek',
        'lastWeek',
        'last30Days',
        'thisMonth',
        'lastMonth',
        'thisYear',
        'lastYear',
      ]
      const expectedLabels = [
        'Today',
        'Last 7 Days',
        'This Week',
        'Last Week',
        'Last 30 Days',
        'This Month',
        'Last Month',
        'This Year',
        'Last Year',
      ]
      presets.forEach((preset, index) => {
        expect(getLabelForPreset(preset)).toBe(expectedLabels[index])
      })
    })
  })

  describe('QUICK_DATE_OPTIONS', () => {
    it('should have 9 options', () => {
      expect(QUICK_DATE_OPTIONS).toHaveLength(9)
    })

    it('should have preset and label for each option', () => {
      QUICK_DATE_OPTIONS.forEach(option => {
        expect(option.preset).toBeDefined()
        expect(option.label).toBeDefined()
        expect(typeof option.preset).toBe('string')
        expect(typeof option.label).toBe('string')
      })
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
      const { fromDate, untilDate, selectedQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      expect(fromDate.value).toBe('')
      expect(untilDate.value).toBe('')
      expect(selectedQuickDate.value).toBe('')
    })

    it('should set dates when setQuickDate is called', async () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      setQuickDate('today')
      expect(fromDate.value).toBe('2024-06-15')
      expect(untilDate.value).toBe('2024-06-15')
      expect(selectedQuickDate.value).toBe('Today')
    })

    it('should call onDateSet callback when dates are set', () => {
      const onDateSet = vi.fn()
      const { setQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
        onDateSet,
      })
      setQuickDate('last7Days')
      expect(onDateSet).toHaveBeenCalledOnce()
    })

    it('should clear all dates when clearDates is called', () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate, clearDates } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      setQuickDate('thisMonth')
      clearDates()
      expect(fromDate.value).toBe('')
      expect(untilDate.value).toBe('')
      expect(selectedQuickDate.value).toBe('')
    })

    it('should set last30Days correctly', () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      setQuickDate('last30Days')
      expect(fromDate.value).toBe('2024-05-17')
      expect(untilDate.value).toBe('2024-06-15')
      expect(selectedQuickDate.value).toBe('Last 30 Days')
    })

    it('should set thisYear correctly', () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      setQuickDate('thisYear')
      expect(fromDate.value).toBe('2024-01-01')
      expect(untilDate.value).toBe('2024-12-31')
      expect(selectedQuickDate.value).toBe('This Year')
    })

    it('should set lastYear correctly', () => {
      const { fromDate, untilDate, selectedQuickDate, setQuickDate } = useQuickDates({
        fromDateKey: 'test_from',
        untilDateKey: 'test_until',
        selectedQuickDateKey: 'test_selected',
      })
      setQuickDate('lastYear')
      expect(fromDate.value).toBe('2023-01-01')
      expect(untilDate.value).toBe('2023-12-31')
      expect(selectedQuickDate.value).toBe('Last Year')
    })
  })
})
