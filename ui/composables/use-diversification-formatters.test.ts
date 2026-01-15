import { describe, it, expect } from 'vitest'
import { useDiversificationFormatters } from './use-diversification-formatters'

describe('useDiversificationFormatters', () => {
  const {
    formatPrice,
    formatTer,
    formatReturn,
    formatPercentage,
    formatCurrency,
    formatRelativeTime,
  } = useDiversificationFormatters()

  describe('formatPrice', () => {
    it('should return dash for null value', () => {
      expect(formatPrice(null)).toBe('-')
    })

    it('should format positive price with euro symbol', () => {
      expect(formatPrice(120.5)).toBe('€120.50')
    })

    it('should format zero price', () => {
      expect(formatPrice(0)).toBe('€0.00')
    })

    it('should format price with two decimal places', () => {
      expect(formatPrice(99.999)).toBe('€100.00')
    })

    it('should format large price', () => {
      expect(formatPrice(12345.67)).toBe('€12345.67')
    })
  })

  describe('formatTer', () => {
    it('should return dash for null value', () => {
      expect(formatTer(null)).toBe('-')
    })

    it('should format TER with default two decimals', () => {
      expect(formatTer(0.22)).toBe('0.22%')
    })

    it('should format TER with custom decimals', () => {
      expect(formatTer(0.22, 3)).toBe('0.220%')
    })

    it('should format zero TER', () => {
      expect(formatTer(0)).toBe('0.00%')
    })

    it('should format TER with rounding', () => {
      expect(formatTer(0.225, 2)).toBe('0.23%')
    })
  })

  describe('formatReturn', () => {
    it('should return dash for null value', () => {
      expect(formatReturn(null)).toBe('-')
    })

    it('should format positive return as percentage', () => {
      expect(formatReturn(0.12)).toBe('12.00%')
    })

    it('should format negative return', () => {
      expect(formatReturn(-0.05)).toBe('-5.00%')
    })

    it('should format zero return', () => {
      expect(formatReturn(0)).toBe('0.00%')
    })

    it('should format small decimal return', () => {
      expect(formatReturn(0.0015)).toBe('0.15%')
    })
  })

  describe('formatPercentage', () => {
    it('should format percentage with two decimals', () => {
      expect(formatPercentage(25.5)).toBe('25.50%')
    })

    it('should format zero percentage', () => {
      expect(formatPercentage(0)).toBe('0.00%')
    })

    it('should format large percentage', () => {
      expect(formatPercentage(100)).toBe('100.00%')
    })

    it('should format percentage with rounding', () => {
      expect(formatPercentage(33.333)).toBe('33.33%')
    })
  })

  describe('formatCurrency', () => {
    it('should format currency with euro symbol', () => {
      const result = formatCurrency(1000)
      expect(result).toContain('1,000')
      expect(result).toContain('€')
    })

    it('should format zero amount', () => {
      const result = formatCurrency(0)
      expect(result).toContain('0')
    })

    it('should format large amount with thousands separator', () => {
      const result = formatCurrency(1234567)
      expect(result).toContain('1,234,567')
    })

    it('should round to whole numbers', () => {
      const result = formatCurrency(1500.99)
      expect(result).toContain('1,501')
    })
  })

  describe('formatRelativeTime', () => {
    it('should return just now for less than 1 minute', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 30000, now)).toBe('just now')
    })

    it('should return 1 min ago for exactly 1 minute', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 60000, now)).toBe('1 min ago')
    })

    it('should return minutes ago for less than 1 hour', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 5 * 60000, now)).toBe('5 min ago')
    })

    it('should return 1 hour ago for exactly 1 hour', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 60 * 60000, now)).toBe('1 hour ago')
    })

    it('should return hours ago for more than 1 hour', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 3 * 60 * 60000, now)).toBe('3 hours ago')
    })

    it('should handle edge case at 59 minutes', () => {
      const now = Date.now()
      expect(formatRelativeTime(now - 59 * 60000, now)).toBe('59 min ago')
    })
  })
})
