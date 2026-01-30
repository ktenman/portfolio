import { describe, it, expect } from 'vitest'
import {
  calculateTargetValue,
  calculateInvestmentAmount,
  calculateRebalanceDifference,
  calculateUnitsFromAmount,
  formatEuroAmount,
} from './diversification-calculations'

describe('diversification-calculations', () => {
  describe('calculateTargetValue', () => {
    it('should calculate target value from portfolio total and allocation percentage', () => {
      expect(calculateTargetValue(10000, 5000, 50)).toBe(7500)
    })

    it('should handle zero current holdings', () => {
      expect(calculateTargetValue(0, 10000, 100)).toBe(10000)
    })

    it('should handle zero investment', () => {
      expect(calculateTargetValue(10000, 0, 50)).toBe(5000)
    })

    it('should handle fractional percentages', () => {
      expect(calculateTargetValue(10000, 0, 33.33)).toBeCloseTo(3333, 0)
    })
  })

  describe('calculateInvestmentAmount', () => {
    it('should calculate amount from total investment and percentage', () => {
      expect(calculateInvestmentAmount(10000, 50)).toBe(5000)
    })

    it('should handle 100 percent allocation', () => {
      expect(calculateInvestmentAmount(10000, 100)).toBe(10000)
    })

    it('should handle zero investment', () => {
      expect(calculateInvestmentAmount(0, 50)).toBe(0)
    })

    it('should handle zero percentage', () => {
      expect(calculateInvestmentAmount(10000, 0)).toBe(0)
    })

    it('should handle fractional percentages', () => {
      expect(calculateInvestmentAmount(10000, 33.33)).toBeCloseTo(3333, 0)
    })
  })

  describe('calculateRebalanceDifference', () => {
    it('should return positive difference when target exceeds current', () => {
      expect(calculateRebalanceDifference(1000, 1500)).toBe(500)
    })

    it('should return positive difference when current exceeds target', () => {
      expect(calculateRebalanceDifference(1500, 1000)).toBe(500)
    })

    it('should return zero when values are equal', () => {
      expect(calculateRebalanceDifference(1000, 1000)).toBe(0)
    })

    it('should handle zero current value', () => {
      expect(calculateRebalanceDifference(0, 1000)).toBe(1000)
    })

    it('should handle zero target value', () => {
      expect(calculateRebalanceDifference(1000, 0)).toBe(1000)
    })
  })

  describe('calculateUnitsFromAmount', () => {
    it('should calculate whole units from amount and price', () => {
      expect(calculateUnitsFromAmount(1000, 100)).toBe(10)
    })

    it('should floor fractional units', () => {
      expect(calculateUnitsFromAmount(1050, 100)).toBe(10)
    })

    it('should return zero for zero price', () => {
      expect(calculateUnitsFromAmount(1000, 0)).toBe(0)
    })

    it('should return zero for negative price', () => {
      expect(calculateUnitsFromAmount(1000, -100)).toBe(0)
    })

    it('should handle amount less than price', () => {
      expect(calculateUnitsFromAmount(50, 100)).toBe(0)
    })
  })

  describe('formatEuroAmount', () => {
    it('should format positive amount with euro symbol', () => {
      expect(formatEuroAmount(1234.56)).toBe('€1234.56')
    })

    it('should return dash for zero amount', () => {
      expect(formatEuroAmount(0)).toBe('-')
    })

    it('should round to two decimal places', () => {
      expect(formatEuroAmount(1234.567)).toBe('€1234.57')
    })

    it('should add trailing zeros for whole numbers', () => {
      expect(formatEuroAmount(1234)).toBe('€1234.00')
    })

    it('should handle small amounts', () => {
      expect(formatEuroAmount(0.01)).toBe('€0.01')
    })
  })
})
