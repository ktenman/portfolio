import { describe, it, expect } from 'vitest'
import { calculateProjection, CalculatorInput } from './portfolio-growth-calculator'

describe('portfolio-growth-calculator', () => {
  describe('calculateProjection', () => {
    it('should generate correct number of year summaries', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 100,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 5,
        taxRate: 22,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries).toHaveLength(5)
      expect(result.portfolioData).toHaveLength(6)
    })

    it('should include initial worth in portfolio data', () => {
      const input: CalculatorInput = {
        initialWorth: 50000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.portfolioData[0]).toBe(50000)
    })

    it('should calculate compound growth correctly over multiple years', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 500,
        yearlyGrowthRate: 0,
        annualReturnRate: 12,
        years: 1,
        taxRate: 22,
      }

      const result = calculateProjection(input)
      const yearOne = result.yearSummaries[0]

      const monthlyRate = Math.pow(1.12, 1 / 12) - 1
      let expectedTotal = 10000
      for (let i = 0; i < 12; i++) {
        expectedTotal += 500
        expectedTotal *= 1 + monthlyRate
      }

      const totalInvested = 10000 + 500 * 12
      const grossProfit = expectedTotal - totalInvested

      expect(yearOne.totalInvested).toBe(totalInvested)
      expect(yearOne.grossProfit).toBeCloseTo(grossProfit, 0)
    })

    it('should apply yearly growth rate to monthly investments', () => {
      const input: CalculatorInput = {
        initialWorth: 0,
        monthlyInvestment: 100,
        yearlyGrowthRate: 10,
        annualReturnRate: 0,
        years: 2,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries[0].totalInvested).toBe(1200)
      expect(result.yearSummaries[1].totalInvested).toBeGreaterThan(2400)
    })

    it('should handle all zero inputs gracefully', () => {
      const input: CalculatorInput = {
        initialWorth: 0,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries).toHaveLength(3)
      result.yearSummaries.forEach(summary => {
        expect(summary.totalWorth).toBe(0)
        expect(summary.grossProfit).toBe(0)
        expect(summary.netWorth).toBe(0)
      })
    })

    it('should calculate tax correctly for each year', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 10,
        years: 3,
        taxRate: 25,
      }

      const result = calculateProjection(input)

      result.yearSummaries.forEach(summary => {
        expect(summary.taxAmount).toBeCloseTo(summary.grossProfit * 0.25, 2)
        expect(summary.netWorth).toBeCloseTo(summary.totalWorth - summary.taxAmount, 2)
      })
    })

    it('should calculate monthly earnings from total worth and annual return rate', () => {
      const input: CalculatorInput = {
        initialWorth: 100000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 12,
        years: 1,
        taxRate: 22,
      }

      const result = calculateProjection(input)
      const yearOne = result.yearSummaries[0]

      expect(yearOne.monthlyEarnings).toBeCloseTo(yearOne.totalWorth * 0.01, 2)
    })

    it('should accumulate investments correctly year over year', () => {
      const input: CalculatorInput = {
        initialWorth: 5000,
        monthlyInvestment: 200,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries[0].totalInvested).toBe(5000 + 200 * 12)
      expect(result.yearSummaries[1].totalInvested).toBe(5000 + 200 * 24)
      expect(result.yearSummaries[2].totalInvested).toBe(5000 + 200 * 36)
    })

    it('should produce consistent results for same input', () => {
      const input: CalculatorInput = {
        initialWorth: 25000,
        monthlyInvestment: 750,
        yearlyGrowthRate: 5,
        annualReturnRate: 8,
        years: 10,
        taxRate: 20,
      }

      const result1 = calculateProjection(input)
      const result2 = calculateProjection(input)

      expect(result1.yearSummaries).toEqual(result2.yearSummaries)
      expect(result1.portfolioData).toEqual(result2.portfolioData)
    })
  })
})
