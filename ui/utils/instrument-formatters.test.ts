import { describe, it, expect } from 'vitest'
import { formatProfit, calculatePortfolioWeight } from './instrument-formatters'

describe('instrument-formatters', () => {
  describe('formatProfit', () => {
    it('should format positive profit without sign prefix', () => {
      const result = formatProfit(100, 'EUR')
      expect(result).toBe('€100.00')
    })

    it('should format negative profit with minus sign', () => {
      const result = formatProfit(-100, 'EUR')
      expect(result).toBe('-€100.00')
    })

    it('should handle zero profit', () => {
      const result = formatProfit(0, 'EUR')
      expect(result).toBe('€0.00')
    })

    it('should use provided currency', () => {
      const result = formatProfit(50, 'USD')
      expect(result).toBe('$50.00')
    })

    it('should default to EUR when currency is undefined', () => {
      const result = formatProfit(75, undefined)
      expect(result).toBe('€75.00')
    })

    it('should handle large positive numbers', () => {
      const result = formatProfit(1234567.89, 'EUR')
      expect(result).toBe('€1,234,567.89')
    })

    it('should handle large negative numbers', () => {
      const result = formatProfit(-1234567.89, 'EUR')
      expect(result).toBe('-€1,234,567.89')
    })

    it('should handle decimal values', () => {
      const result = formatProfit(123.456, 'EUR')
      expect(result).toBe('€123.46')
    })
  })

  describe('calculatePortfolioWeight', () => {
    it('should calculate percentage correctly', () => {
      const result = calculatePortfolioWeight(1000, 10000)
      expect(result).toBe('10.00%')
    })

    it('should return 0.00% when total value is 0', () => {
      const result = calculatePortfolioWeight(500, 0)
      expect(result).toBe('0.00%')
    })

    it('should format to 2 decimal places', () => {
      const result = calculatePortfolioWeight(1, 3)
      expect(result).toBe('33.33%')
    })

    it('should handle 100% weight', () => {
      const result = calculatePortfolioWeight(5000, 5000)
      expect(result).toBe('100.00%')
    })

    it('should handle small percentages', () => {
      const result = calculatePortfolioWeight(1, 10000)
      expect(result).toBe('0.01%')
    })

    it('should handle zero instrument value', () => {
      const result = calculatePortfolioWeight(0, 10000)
      expect(result).toBe('0.00%')
    })

    it('should handle very small weight', () => {
      const result = calculatePortfolioWeight(0.001, 100)
      expect(result).toBe('0.00%')
    })
  })
})
