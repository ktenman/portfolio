import { describe, it, expect } from 'vitest'
import { useFormatters } from './use-formatters'

describe('useFormatters', () => {
  const { formatCurrency, formatNumber, formatPercentage, formatDate, formatProfitLoss } =
    useFormatters()

  describe('formatCurrency', () => {
    it('formats positive numbers correctly', () => {
      expect(formatCurrency(100)).toBe('€100.00')
      expect(formatCurrency(1234.56)).toBe('€1234.56')
      expect(formatCurrency(0.5)).toBe('€0.50')
    })

    it('formats negative numbers as positive with euro sign', () => {
      expect(formatCurrency(-100)).toBe('€100.00')
      expect(formatCurrency(-1234.56)).toBe('€1234.56')
    })

    it('handles null and undefined', () => {
      expect(formatCurrency(null)).toBe('€0.00')
      expect(formatCurrency(undefined)).toBe('€0.00')
    })

    it('formats zero correctly', () => {
      expect(formatCurrency(0)).toBe('€0.00')
    })
  })

  describe('formatNumber', () => {
    it('formats small positive numbers in exponential notation', () => {
      expect(formatNumber(0.0001)).toBe('1.000 * 10^-4')
      expect(formatNumber(0.5)).toBe('5.000 * 10^-1')
    })

    it('formats single digit numbers with 3 decimal places', () => {
      expect(formatNumber(1)).toBe('1.000')
      expect(formatNumber(9.5)).toBe('9.500')
    })

    it('formats multi-digit numbers with 2 decimal places', () => {
      expect(formatNumber(10)).toBe('10.00')
      expect(formatNumber(123.456)).toBe('123.46')
      expect(formatNumber(1000.1)).toBe('1000.10')
    })

    it('handles null and undefined', () => {
      expect(formatNumber(null)).toBe('')
      expect(formatNumber(undefined)).toBe('')
    })

    it('formats zero correctly', () => {
      expect(formatNumber(0)).toBe('0.000')
    })
  })

  describe('formatPercentage', () => {
    it('formats percentages correctly', () => {
      expect(formatPercentage(0.1)).toBe('10.00%')
      expect(formatPercentage(0.25)).toBe('25.00%')
      expect(formatPercentage(1)).toBe('100.00%')
      expect(formatPercentage(1.5)).toBe('150.00%')
    })

    it('handles negative percentages', () => {
      expect(formatPercentage(-0.1)).toBe('-10.00%')
      expect(formatPercentage(-0.25)).toBe('-25.00%')
    })

    it('handles null and undefined', () => {
      expect(formatPercentage(null)).toBe('0.00%')
      expect(formatPercentage(undefined)).toBe('0.00%')
    })

    it('formats zero correctly', () => {
      expect(formatPercentage(0)).toBe('0.00%')
    })
  })

  describe('formatDate', () => {
    it('formats dates with 2-digit year by default', () => {
      expect(formatDate('2024-01-01')).toBe('01.01.24')
      expect(formatDate('2024-12-31')).toBe('31.12.24')
    })

    it('formats dates with 4-digit year when fullYear is true', () => {
      expect(formatDate('2024-01-01', true)).toBe('01.01.2024')
      expect(formatDate('2024-12-31', true)).toBe('31.12.2024')
    })

    it('handles ISO date strings', () => {
      expect(formatDate('2024-03-15T10:30:00Z')).toBe('15.03.24')
      expect(formatDate('2024-03-15T10:30:00Z', true)).toBe('15.03.2024')
    })

    it('returns empty string for empty input', () => {
      expect(formatDate('')).toBe('')
    })
  })

  describe('formatProfitLoss', () => {
    it('formats positive values with plus sign', () => {
      expect(formatProfitLoss(100)).toBe('+100.00')
      expect(formatProfitLoss(1234.56)).toBe('+1234.56')
    })

    it('formats negative values with minus sign', () => {
      expect(formatProfitLoss(-100)).toBe('-100.00')
      expect(formatProfitLoss(-1234.56)).toBe('-1234.56')
    })

    it('handles null and undefined', () => {
      expect(formatProfitLoss(null)).toBe('0.00')
      expect(formatProfitLoss(undefined)).toBe('0.00')
    })

    it('formats zero without sign', () => {
      expect(formatProfitLoss(0)).toBe('+0.00')
    })
  })
})
