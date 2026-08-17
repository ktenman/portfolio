import { describe, it, expect } from 'vitest'
import { calculatePortfolioWeight } from './instrument-formatters'

describe('instrument-formatters', () => {
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
