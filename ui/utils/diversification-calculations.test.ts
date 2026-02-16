import { describe, it, expect } from 'vitest'
import {
  calculateTargetValue,
  calculateInvestmentAmount,
  calculateRebalanceDifference,
  calculateUnitsFromAmount,
  formatEuroAmount,
  calculateBudgetConstrainedRebalance,
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

  describe('calculateBudgetConstrainedRebalance', () => {
    it('should return null when total buy needs fit within budget', () => {
      const entries = [
        { id: 1, price: 50, difference: 200, isBuy: true },
        { id: 2, price: 30, difference: 100, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 500, false)
      expect(result).toBeNull()
    })

    it('should return null when no buy entries exist', () => {
      const entries = [{ id: 1, price: 50, difference: -200, isBuy: false }]
      const result = calculateBudgetConstrainedRebalance(entries, 100, false)
      expect(result).toBeNull()
    })

    it('should constrain buys proportionally when total exceeds budget', () => {
      const entries = [
        { id: 1, price: 14.24, difference: 94.18, isBuy: true },
        { id: 2, price: 8.54, difference: 32.82, isBuy: true },
        { id: 3, price: 6.44, difference: 84.46, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 100, false)
      expect(result).not.toBeNull()
      const totalSpent = Array.from(result!.allocations.values()).reduce(
        (sum, a) =>
          sum +
          a.units *
            entries.find(
              e => e.id === Array.from(result!.allocations.entries()).find(([, v]) => v === a)?.[0]
            )!.price,
        0
      )
      expect(totalSpent).toBeLessThanOrEqual(100)
    })

    it('should not exceed budget with real-world scenario from screenshot', () => {
      const entries = [
        { id: 1, price: 14.24, difference: 94.18, isBuy: true },
        { id: 2, price: 8.54, difference: 32.82, isBuy: true },
        { id: 3, price: 6.44, difference: 84.46, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 100, false)!
      let spent = 0
      for (const [id, data] of result.allocations) {
        const entry = entries.find(e => e.id === id)!
        spent += data.units * entry.price
      }
      expect(spent).toBeLessThanOrEqual(100)
      expect(spent + result.totalRemaining).toBeCloseTo(100, 1)
    })

    it('should allocate more units with optimize enabled', () => {
      const entries = [
        { id: 1, price: 14.24, difference: 94.18, isBuy: true },
        { id: 2, price: 8.54, difference: 32.82, isBuy: true },
        { id: 3, price: 6.44, difference: 84.46, isBuy: true },
      ]
      const withoutOptimize = calculateBudgetConstrainedRebalance(entries, 100, false)!
      const withOptimize = calculateBudgetConstrainedRebalance(entries, 100, true)!
      const unitsWithout = Array.from(withoutOptimize.allocations.values()).reduce(
        (sum, a) => sum + a.units,
        0
      )
      const unitsWith = Array.from(withOptimize.allocations.values()).reduce(
        (sum, a) => sum + a.units,
        0
      )
      expect(unitsWith).toBeGreaterThanOrEqual(unitsWithout)
      expect(withOptimize.totalRemaining).toBeLessThanOrEqual(withoutOptimize.totalRemaining)
    })

    it('should account for sell proceeds when constraining buys', () => {
      const entries = [
        { id: 1, price: 120.5, difference: -1000, isBuy: false },
        { id: 2, price: 95.3, difference: 2000, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 1000, false)
      expect(result).not.toBeNull()
      const buyData = result!.allocations.get(2)!
      expect(buyData.units).toBe(20)
    })

    it('should return null when sell proceeds plus budget cover all buys', () => {
      const entries = [
        { id: 1, price: 100, difference: -500, isBuy: false },
        { id: 2, price: 50, difference: 300, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 200, false)
      expect(result).toBeNull()
    })

    it('should handle single buy entry exceeding budget', () => {
      const entries = [{ id: 1, price: 200, difference: 1000, isBuy: true }]
      const result = calculateBudgetConstrainedRebalance(entries, 100, false)!
      expect(result.allocations.get(1)!.units).toBe(0)
      expect(result.totalRemaining).toBe(100)
    })

    it('should handle optimize with single buy entry exceeding budget', () => {
      const entries = [{ id: 1, price: 50, difference: 500, isBuy: true }]
      const result = calculateBudgetConstrainedRebalance(entries, 100, true)!
      expect(result.allocations.get(1)!.units).toBe(2)
      expect(result.totalRemaining).toBe(0)
    })
  })
})
