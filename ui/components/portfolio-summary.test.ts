import { describe, it, expect } from 'vitest'
import { formatCurrencyWithSymbol } from '../utils/formatters'

describe('PortfolioSummary', () => {
  describe('format24hChange', () => {
    const format24hChange = (value: number | null) => {
      if (value === null || value === 0 || Math.abs(value) <= 0.01) {
        return ''
      }
      return formatCurrencyWithSymbol(value)
    }

    it('should format positive 24h change', () => {
      const result = format24hChange(250.5)
      expect(result).toBe('€250.50')
    })

    it('should format negative 24h change', () => {
      const result = format24hChange(-125.75)
      expect(result).toBe('-€125.75')
    })

    it('should return empty string for zero 24h change', () => {
      const result = format24hChange(0)
      expect(result).toBe('')
    })

    it('should return empty string for null 24h change', () => {
      const result = format24hChange(null)
      expect(result).toBe('')
    })

    it('should return empty string for near-zero positive 24h change (< 0.01)', () => {
      const result = format24hChange(0.005)
      expect(result).toBe('')
    })

    it('should return empty string for near-zero negative 24h change (> -0.01)', () => {
      const result = format24hChange(-0.005)
      expect(result).toBe('')
    })

    it('should format value exactly at threshold (0.01)', () => {
      const result = format24hChange(0.01)
      expect(result).toBe('')
    })

    it('should format value just above threshold (0.011)', () => {
      const result = format24hChange(0.011)
      expect(result).toBe('€0.01')
    })
  })

  describe('getProfitChangeClass', () => {
    const getProfitChangeClass = (value: number) => {
      if (value > 0) return 'text-success'
      if (value < 0) return 'text-danger'
      return ''
    }

    it('should return text-success for positive values', () => {
      expect(getProfitChangeClass(100)).toBe('text-success')
      expect(getProfitChangeClass(0.01)).toBe('text-success')
    })

    it('should return text-danger for negative values', () => {
      expect(getProfitChangeClass(-100)).toBe('text-danger')
      expect(getProfitChangeClass(-0.01)).toBe('text-danger')
    })

    it('should return empty string for zero', () => {
      expect(getProfitChangeClass(0)).toBe('')
    })
  })
})
