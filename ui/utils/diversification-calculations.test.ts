import { describe, it, expect } from 'vitest'
import {
  calculateTargetValue,
  calculateInvestmentAmount,
  calculateUnitsFromAmount,
  formatEuroAmount,
  optimizeRebalanceUnits,
  optimizeInvestmentAllocation,
  calculateBudgetConstrainedRebalance,
} from './diversification-calculations'

describe('diversification-calculations', () => {
  describe('calculateTargetValue', () => {
    it.each([
      [10000, 5000, 50, 7500],
      [0, 10000, 100, 10000],
      [10000, 0, 50, 5000],
      [10000, 0, 33.33, 3333],
    ] as const)(
      'sizes holdings %s plus investment %s at %s%% to %s',
      (holdings, investment, pct, out) => {
        expect(calculateTargetValue(holdings, investment, pct)).toBeCloseTo(out, 0)
      }
    )
  })

  describe('calculateInvestmentAmount', () => {
    it.each([
      [10000, 50, 5000],
      [10000, 100, 10000],
      [0, 50, 0],
      [10000, 0, 0],
      [10000, 33.33, 3333],
    ] as const)('allocates %s at %s%% as %s', (investment, percentage, expected) => {
      expect(calculateInvestmentAmount(investment, percentage)).toBeCloseTo(expected, 0)
    })
  })

  describe('calculateUnitsFromAmount', () => {
    it.each([
      [1000, 100, 10],
      [1050, 100, 10],
      [1000, 0, 0],
      [1000, -100, 0],
      [50, 100, 0],
    ] as const)('converts %s at price %s into %s units', (amount, price, expected) => {
      expect(calculateUnitsFromAmount(amount, price)).toBe(expected)
    })
  })

  describe('formatEuroAmount', () => {
    it.each([
      [1234.56, '€1234.56'],
      [0, '-'],
      [1234.567, '€1234.57'],
      [1234, '€1234.00'],
      [0.01, '€0.01'],
    ] as const)('formats %s as %s', (amount, expected) => {
      expect(formatEuroAmount(amount)).toBe(expected)
    })
  })

  describe('optimizeRebalanceUnits', () => {
    it('should return base units when no buy entries exist', () => {
      const entries = [{ id: 1, units: 5, isBuy: false, difference: -200, unused: 0, price: 40 }]
      const result = optimizeRebalanceUnits(entries)
      expect(result.allocations.get(1)!.units).toBe(5)
      expect(result.allocations.get(1)!.isBuy).toBe(false)
      expect(result.totalRemaining).toBe(0)
    })

    it('should redistribute unused amounts to buy entries', () => {
      const entries = [
        { id: 1, units: 3, isBuy: true, difference: 150, unused: 20, price: 40 },
        { id: 2, units: 5, isBuy: true, difference: 100, unused: 15, price: 10 },
      ]
      const result = optimizeRebalanceUnits(entries)
      const fund1 = result.allocations.get(1)!
      const fund2 = result.allocations.get(2)!
      expect(fund1.units + fund2.units).toBeGreaterThanOrEqual(3 + 5)
    })

    it('should handle empty entries array', () => {
      const result = optimizeRebalanceUnits([])
      expect(result.allocations.size).toBe(0)
      expect(result.totalRemaining).toBe(0)
    })

    it('should not add units when unused is less than any price', () => {
      const entries = [{ id: 1, units: 2, isBuy: true, difference: 500, unused: 5, price: 100 }]
      const result = optimizeRebalanceUnits(entries)
      expect(result.allocations.get(1)!.units).toBe(2)
      expect(result.totalRemaining).toBe(5)
    })

    it('should handle entries with null price', () => {
      const entries = [{ id: 1, units: 0, isBuy: true, difference: 100, unused: 50, price: null }]
      const result = optimizeRebalanceUnits(entries)
      expect(result.allocations.get(1)!.units).toBe(0)
    })
  })

  describe('optimizeInvestmentAllocation', () => {
    it('should allocate units proportionally to percentages', () => {
      const entries = [
        { id: 1, price: 50, percentage: 60 },
        { id: 2, price: 30, percentage: 40 },
      ]
      const result = optimizeInvestmentAllocation(entries, 1000)
      const fund1Units = result.get(1)!
      const fund2Units = result.get(2)!
      expect(fund1Units * 50 + fund2Units * 30).toBeLessThanOrEqual(1000)
      expect(fund1Units).toBeGreaterThan(0)
      expect(fund2Units).toBeGreaterThan(0)
    })

    it('should return empty map for empty entries', () => {
      const result = optimizeInvestmentAllocation([], 1000)
      expect(result.size).toBe(0)
    })

    it('should handle zero price entries', () => {
      const entries = [
        { id: 1, price: 0, percentage: 50 },
        { id: 2, price: 25, percentage: 50 },
      ]
      const result = optimizeInvestmentAllocation(entries, 100)
      expect(result.get(1)).toBe(0)
      expect(result.get(2)!).toBe(4)
    })

    it('should maximize allocation when remainders allow extra units', () => {
      const entries = [
        { id: 1, price: 33, percentage: 50 },
        { id: 2, price: 33, percentage: 50 },
      ]
      const result = optimizeInvestmentAllocation(entries, 100)
      const totalSpent = (result.get(1)! + result.get(2)!) * 33
      expect(totalSpent).toBeLessThanOrEqual(100)
      expect(result.get(1)! + result.get(2)!).toBe(3)
    })

    it('should respect target percentages when distributing extra units', () => {
      const entries = [
        { id: 1, price: 10, percentage: 70 },
        { id: 2, price: 10, percentage: 30 },
      ]
      const result = optimizeInvestmentAllocation(entries, 100)
      expect(result.get(1)!).toBeGreaterThan(result.get(2)!)
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

    it('should constrain buys proportionally and account for full budget', () => {
      const entries = [
        { id: 1, price: 14.24, difference: 94.18, isBuy: true },
        { id: 2, price: 8.54, difference: 32.82, isBuy: true },
        { id: 3, price: 6.44, difference: 84.46, isBuy: true },
      ]
      const result = calculateBudgetConstrainedRebalance(entries, 100, false)!
      expect(result).not.toBeNull()
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
